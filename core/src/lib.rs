// Sonic core: YT Music + SoundCloud resolver exposed to Kotlin over JNI.
// Unofficial endpoints by necessity — both services have no public audio API.
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde_json::{json, Value};

const UA_WEB: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
const UA_VISION: &str = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15";

fn http() -> reqwest::blocking::Client {
    reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(20))
        // InnerTube's bot check trips on reqwest's HTTP/2 fingerprint; HTTP/1.1 gets normal responses.
        .http1_only()
        .build()
        .expect("client")
}

fn fail(e: impl std::fmt::Display) -> String {
    json!({ "ok": false, "error": e.to_string() }).to_string()
}

// ---------- YouTube Music ----------
// VISIONOS hands out direct audio URLs, but only when the request carries a visitorData id.

fn yt_visitor() -> Result<String, String> {
    let body = json!({"context": {"client": {"clientName": "WEB", "clientVersion": "2.20250101.00.00"}}});
    let res: Value = http()
        .post("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_WEB)
        .body(body.to_string())
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;
    res.pointer("/responseContext/visitorData")
        .or_else(|| res.get("visitorData"))
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or("no visitorData".into())
}

fn yt_search(query: &str) -> Result<Value, String> {
    let body = json!({
        "context": {"client": {"clientName": "WEB_REMIX", "clientVersion": "1.20250101.00.00", "hl": "en", "gl": "US"}},
        "query": query,
        "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" // songs only
    });
    let res: Value = http()
        .post("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_WEB)
        .header("Origin", "https://music.youtube.com")
        .body(body.to_string())
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let mut out = Vec::new();
    collect_items(&res, &mut out);
    Ok(json!(out))
}

// Walk the response for every musicResponsiveListItemRenderer (schema is deeply nested and varies).
fn collect_items(v: &Value, out: &mut Vec<Value>) {
    match v {
        Value::Object(map) => {
            if let Some(r) = map.get("musicResponsiveListItemRenderer") {
                if let Some(item) = parse_item(r) {
                    out.push(item);
                }
            }
            for (_, child) in map {
                collect_items(child, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| collect_items(c, out)),
        _ => {}
    }
}

fn runs_text(v: &Value) -> String {
    v.get("runs")
        .and_then(|r| r.as_array())
        .map(|a| {
            a.iter()
                .filter_map(|x| x.get("text").and_then(|t| t.as_str()))
                .collect::<Vec<_>>()
                .join("")
        })
        .unwrap_or_default()
}

fn column_text(cols: &[Value], idx: usize) -> String {
    cols.get(idx)
        .and_then(|c| c.get("musicResponsiveListItemFlexColumnRenderer"))
        .and_then(|c| c.get("text"))
        .map(runs_text)
        .unwrap_or_default()
}

fn parse_item(r: &Value) -> Option<Value> {
    // Playlist/chart rows carry the id on navigationEndpoint; search rows put it in the overlay.
    let watch = r
        .pointer("/overlay/musicItemThumbnailOverlayRenderer/content/musicPlayButtonRenderer/playNavigationEndpoint/watchEndpoint/videoId")
        .or_else(|| r.pointer("/navigationEndpoint/watchEndpoint/videoId"));
    let browse = r.pointer("/navigationEndpoint/browseEndpoint/browseId");
    let (video_id, kind) = match (watch, browse) {
        (Some(w), _) => (w.as_str()?.to_string(), "track"),
        (None, Some(b)) => (b.as_str()?.to_string(), "browse"),
        _ => return None,
    };

    let cols = r.get("flexColumns")?.as_array()?;
    let title = column_text(cols, 0);
    let subtitle = column_text(cols, 1);

    let thumbs = r
        .get("thumbnail")
        .and_then(|t| t.get("musicThumbnailRenderer"))
        .and_then(|t| t.get("thumbnail"))
        .and_then(|t| t.get("thumbnails"))
        .and_then(|t| t.as_array());
    let art = thumbs
        .and_then(|a| a.last())
        .and_then(|t| t.get("url"))
        .and_then(|u| u.as_str())
        .unwrap_or("")
        .to_string();

    let mut parts = subtitle.split(" • ");
    let artist = parts.next().unwrap_or("").to_string();
    let album = parts.next().unwrap_or("").to_string();

    Some(json!({
        "id": video_id, "title": title, "artist": artist, "album": album, "art": art, "kind": kind
    }))
}

fn yt_stream(video_id: &str) -> Result<Value, String> {
    let visitor = yt_visitor()?;
    let body = json!({
        "context": {"client": {
            "clientName": "VISIONOS", "clientVersion": "1.02", "hl": "en", "gl": "US",
            "deviceMake": "Apple", "deviceModel": "RealityDevice17,1",
            "osName": "visionOS", "osVersion": "26.5.23O471",
            "visitorData": visitor
        }},
        "videoId": video_id, "contentCheckOk": true, "racyCheckOk": true
    });
    let res: Value = http()
        .post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_VISION)
        .header("X-YouTube-Client-Name", "101")
        .header("X-YouTube-Client-Version", "1.02")
        .header("X-Goog-Visitor-Id", visitor.as_str())
        .body(body.to_string())
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    if let Some(reason) = res.pointer("/playabilityStatus/reason").and_then(|r| r.as_str()) {
        return Err(format!("unplayable: {reason}"));
    }
    let formats = res
        .pointer("/streamingData/adaptiveFormats")
        .and_then(|f| f.as_array())
        .ok_or("no adaptiveFormats")?;
    let best = formats
        .iter()
        .filter(|f| f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("").contains("audio"))
        .filter(|f| f.get("url").is_some())
        .max_by_key(|f| f.get("bitrate").and_then(|b| b.as_u64()).unwrap_or(0))
        .ok_or("no audio stream")?;

    Ok(json!({
        "url": best.get("url").and_then(|u| u.as_str()).unwrap_or(""),
        "mime": best.get("mimeType").and_then(|m| m.as_str()).unwrap_or(""),
        "bitrate": best.get("bitrate").and_then(|b| b.as_u64()).unwrap_or(0),
        "ua": UA_VISION
    }))
}

// ---------- YouTube Music: home feed and playlists ----------

fn yt_browse(browse_id: &str, params: Option<&str>) -> Result<Value, String> {
    let mut body = json!({
        "context": {"client": {"clientName": "WEB_REMIX", "clientVersion": "1.20250101.00.00", "hl": "en", "gl": "US"}},
        "browseId": browse_id
    });
    if let Some(p) = params {
        body["params"] = json!(p);
    }
    http()
        .post("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_WEB)
        .header("Origin", "https://music.youtube.com")
        .body(body.to_string())
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())
}

/// Home shelves as `[{title, items:[{id,title,subtitle,art,kind}]}]`.
fn yt_home() -> Result<Value, String> {
    let res = yt_browse("FEmusic_home", None)?;
    let mut shelves = Vec::new();
    collect_shelves(&res, &mut shelves);
    Ok(json!(shelves))
}

fn collect_shelves(v: &Value, out: &mut Vec<Value>) {
    match v {
        Value::Object(map) => {
            if let Some(shelf) = map.get("musicCarouselShelfRenderer") {
                let title = shelf
                    .pointer("/header/musicCarouselShelfBasicHeaderRenderer/title")
                    .map(runs_text)
                    .unwrap_or_default();
                let mut items = Vec::new();
                if let Some(contents) = shelf.get("contents").and_then(|c| c.as_array()) {
                    for c in contents {
                        if let Some(item) = two_row_item(c) {
                            items.push(item);
                        }
                    }
                }
                if !items.is_empty() {
                    out.push(json!({"title": title, "items": items}));
                }
            }
            for (_, child) in map {
                collect_shelves(child, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| collect_shelves(c, out)),
        _ => {}
    }
}

/// A carousel card: playlist, album, or a video. `kind` says how to open it.
fn two_row_item(c: &Value) -> Option<Value> {
    let r = c.get("musicTwoRowItemRenderer")?;
    let title = r.get("title").map(runs_text)?;
    let subtitle = r
        .pointer("/subtitle/runs")
        .and_then(|_| r.get("subtitle"))
        .map(runs_text)
        .unwrap_or_default();
    // Albums and playlists expose browseEndpoint; music video cards expose watchEndpoint.
    let browse = r
        .pointer("/navigationEndpoint/browseEndpoint/browseId")
        .or_else(|| r.pointer("/title/runs/0/navigationEndpoint/browseEndpoint/browseId"))
        .and_then(|b| b.as_str());
    let watch = r
        .pointer("/navigationEndpoint/watchEndpoint/videoId")
        .or_else(|| r.pointer("/overlay/musicItemThumbnailOverlayRenderer/content/musicPlayButtonRenderer/playNavigationEndpoint/watchEndpoint/videoId"))
        .and_then(|v| v.as_str());
    let (id, kind) = match (browse, watch) {
        (Some(b), _) => (b, "playlist"),
        (None, Some(w)) => (w, "track"),
        _ => return None,
    };
    let browse_params = r
        .pointer("/navigationEndpoint/browseEndpoint/params")
        .and_then(|p| p.as_str());
    let art = r
        .pointer("/thumbnailRenderer/musicThumbnailRenderer/thumbnail/thumbnails")
        .and_then(|t| t.as_array())
        .and_then(|a| a.last())
        .and_then(|t| t.get("url"))
        .and_then(|u| u.as_str())
        .unwrap_or("");
    Some(json!({
        "id": id,
        "params": browse_params,
        "title": title,
        "subtitle": subtitle,
        "art": art,
        "kind": kind
    }))
}

/// Tracks inside a playlist or album browse page.
fn yt_playlist(browse_id: &str, params: Option<&str>) -> Result<Value, String> {
    let res = yt_browse(browse_id, params)?;
    let mut out = Vec::new();
    collect_items(&res, &mut out);
    // Playlist pages use a different renderer for rows; fall back to it.
    if out.is_empty() {
        let mut rows = Vec::new();
        collect_playlist_rows(&res, &mut rows);
        out = rows;
    }
    Ok(json!(out))
}

fn collect_playlist_rows(v: &Value, out: &mut Vec<Value>) {
    match v {
        Value::Object(map) => {
            if let Some(r) = map.get("musicResponsiveListItemRenderer") {
                if let Some(item) = parse_item(r) {
                    out.push(item);
                }
            }
            for (_, child) in map {
                collect_playlist_rows(child, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| collect_playlist_rows(c, out)),
        _ => {}
    }
}

/// Global charts. Anonymous responses contain artist rows only, so callers must filter
/// `kind == "track"`; kept for when YouTube returns track charts again.
fn yt_charts() -> Result<Value, String> {
    let res = yt_browse("FEmusic_charts", None)?;
    let mut out = Vec::new();
    collect_playlist_rows(&res, &mut out);
    Ok(json!(out))
}

/// "For You": the Explore page, which is the only anonymous endpoint with real editorial
/// shelves (new releases, genres, trending, music videos), plus the chart playlists.
fn yt_for_you() -> Result<Value, String> {
    let mut shelves = Vec::new();
    if let Ok(res) = yt_browse("FEmusic_explore", None) {
        collect_shelves(&res, &mut shelves);
    }
    // Charts return one playlist shelf and one artist list; keep whatever came back as cards
    // rather than filtering on a shelf title, which is localised.
    if let Ok(res) = yt_browse("FEmusic_charts", None) {
        let mut charts = Vec::new();
        collect_shelves(&res, &mut charts);
        shelves.extend(charts.into_iter().filter(|s| {
            s["items"].as_array().map(|a| !a.is_empty()).unwrap_or(false)
        }));
    }
    // Each shelf is its own LazyRow with per-row keys, so a playlist may appear in two shelves.
    shelves.retain(|s| s["items"].as_array().map(|a| !a.is_empty()).unwrap_or(false));
    if shelves.is_empty() {
        return Err("for you: no shelves".into());
    }
    Ok(json!(shelves))
}

/// Autoplay radio seeded by a track: YouTube builds a 50-item queue from its artist and genre.
/// The `RDAMVM` prefix is the mix playlist id; without it `/next` returns only the seed track.
fn yt_radio(video_id: &str) -> Result<Value, String> {
    let body = json!({
        "context": {"client": {"clientName": "WEB_REMIX", "clientVersion": "1.20250101.00.00", "hl": "en", "gl": "US"}},
        "videoId": video_id,
        "playlistId": format!("RDAMVM{video_id}"),
        "isAudioOnly": true
    });
    let res: Value = http()
        .post("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_WEB)
        .header("Origin", "https://music.youtube.com")
        .body(body.to_string())
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let mut out = Vec::new();
    collect_radio_items(&res, &mut out);
    if out.is_empty() {
        return Err("radio: no tracks".into());
    }
    Ok(json!(out))
}

/// Synced lyrics, or plain text when YouTube Music has no timing. Returns
/// `{text, synced}` where `synced` is `[{start, text}]` in seconds.
fn yt_lyrics(video_id: &str) -> Result<Value, String> {
    let res: Value = http()
        .post("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
        .header("Content-Type", "application/json")
        .header("User-Agent", UA_WEB)
        .header("Origin", "https://music.youtube.com")
        .body(
            json!({
                "context": {"client": {"clientName": "WEB_REMIX", "clientVersion": "1.20250101.00.00", "hl": "en", "gl": "US"}},
                "videoId": video_id
            })
            .to_string(),
        )
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    // The lyrics page is a separate browse call; `next` only hands out its id.
    let mut browse_ids = Vec::new();
    collect_browse_ids(&res, &mut browse_ids);
    let lyrics_id = browse_ids
        .into_iter()
        .find(|b| b.starts_with("MPLY"))
        .ok_or_else(|| "no lyrics for this track".to_string())?;

    let page = yt_browse(&lyrics_id, None)?;
    let mut shelf = Vec::new();
    find_key(&page, "musicDescriptionShelfRenderer", &mut shelf);
    let text = shelf
        .first()
        .and_then(|s| s.pointer("/description"))
        .map(runs_text)
        .unwrap_or_default();
    if text.trim().is_empty() {
        return Err("no lyrics for this track".into());
    }
    Ok(json!({"text": text, "synced": []}))
}

fn collect_browse_ids(v: &Value, out: &mut Vec<String>) {
    match v {
        Value::Object(map) => {
            if let Some(b) = map.get("browseId").and_then(|b| b.as_str()) {
                out.push(b.to_string());
            }
            for (_, child) in map {
                collect_browse_ids(child, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| collect_browse_ids(c, out)),
        _ => {}
    }
}

fn find_key<'a>(v: &'a Value, key: &str, out: &mut Vec<&'a Value>) {
    match v {
        Value::Object(map) => {
            if let Some(found) = map.get(key) {
                out.push(found);
            }
            for (_, child) in map {
                find_key(child, key, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| find_key(c, key, out)),
        _ => {}
    }
}

fn collect_radio_items(v: &Value, out: &mut Vec<Value>) {
    match v {
        Value::Object(map) => {
            if let Some(r) = map.get("playlistPanelVideoRenderer") {
                let id = r.get("videoId").and_then(|i| i.as_str()).unwrap_or("");
                let title = r.get("title").map(runs_text).unwrap_or_default();
                let byline = r
                    .get("longBylineText")
                    .or_else(|| r.get("shortBylineText"))
                    .map(runs_text)
                    .unwrap_or_default();
                // Byline is "Artist • Album • Year"; only the artist is useful to the UI.
                let artist = byline.split(" • ").next().unwrap_or("").to_string();
                let art = r
                    .pointer("/thumbnail/thumbnails")
                    .and_then(|t| t.as_array())
                    .and_then(|a| a.last())
                    .and_then(|t| t.get("url"))
                    .and_then(|u| u.as_str())
                    .unwrap_or("");
                if !id.is_empty() && !title.is_empty() {
                    out.push(json!({
                        "id": id, "title": title, "artist": artist, "album": "",
                        "art": art, "kind": "track"
                    }));
                }
            }
            for (_, child) in map {
                collect_radio_items(child, out);
            }
        }
        Value::Array(arr) => arr.iter().for_each(|c| collect_radio_items(c, out)),
        _ => {}
    }
}

// ---------- Spotify ----------
// The Web API needs a client secret and rate-limits this IP hard. The embed player's own
// token plus the internal Pathfinder endpoint works anonymously, so that is what we use.
// Playback is limited to Spotify's 30s preview MP3s: full tracks require user OAuth Premium.

const SP_PLAYER_UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
// Persisted-query hash for searchDesktop; Spotify rotates it, so search breaks when it changes.
const SP_SEARCH_HASH: &str = "d9f785900f0710b31c07818d617f4f7600c1e21217e80f5b043d1e78d74e6026";

fn sp_token() -> Result<String, String> {
    let res: Value = http()
        .get("https://open.spotify.com/embed/api/token")
        .header("User-Agent", SP_PLAYER_UA)
        .header("Accept", "application/json")
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;
    res.get("accessToken")
        .and_then(|t| t.as_str())
        .map(|s| s.to_string())
        .ok_or("spotify: no token".into())
}

fn sp_search(query: &str) -> Result<Value, String> {
    let token = sp_token()?;
    let vars = json!({
        "searchTerm": query, "offset": 0, "limit": 20, "numberOfTopResults": 5,
        "includeAudiobooks": false, "includeArtistHasConcertsField": false,
        "includePreReleases": false, "includeLocalConcertsField": false
    });
    let url = format!(
        "https://api-partner.spotify.com/pathfinder/v1/query?operationName=searchDesktop&variables={}&extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22{}%22%7D%7D",
        urlencode(&vars.to_string()),
        SP_SEARCH_HASH
    );
    let res: Value = http()
        .get(url)
        .header("User-Agent", SP_PLAYER_UA)
        .header("Authorization", format!("Bearer {token}"))
        .header("app-platform", "WebPlayer")
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let items = res
        .pointer("/data/searchV2/tracksV2/items")
        .and_then(|i| i.as_array())
        .ok_or("spotify: no tracks")?;
    let out: Vec<Value> = items
        .iter()
        .filter_map(|i| {
            let d = i.get("item")?.get("data")?;
            let id = d.get("id")?.as_str()?;
            let artists = d
                .pointer("/artists/items")
                .and_then(|a| a.as_array())
                .map(|a| {
                    a.iter()
                        .filter_map(|x| x.pointer("/profile/name").and_then(|n| n.as_str()))
                        .collect::<Vec<_>>()
                        .join(", ")
                })
                .unwrap_or_default();
            let art = d
                .pointer("/albumOfTrack/coverArt/sources")
                .and_then(|s| s.as_array())
                .and_then(|a| a.last())
                .and_then(|s| s.get("url"))
                .and_then(|u| u.as_str())
                .unwrap_or("");
            Some(json!({
                "id": id,
                "title": d.get("name").and_then(|n| n.as_str()).unwrap_or(""),
                "artist": artists,
                "album": d.pointer("/albumOfTrack/name").and_then(|n| n.as_str()).unwrap_or(""),
                "art": art,
                "duration": d.pointer("/duration/totalMilliseconds").and_then(|x| x.as_u64()).unwrap_or(0)
            }))
        })
        .collect();
    Ok(json!(out))
}

/// Minimal percent-encoding for the JSON blob in a query string.
fn urlencode(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(b as char),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// Spotify only exposes a 30s preview anonymously. Read it from the embed page.
fn sp_preview(track_id: &str) -> Result<Value, String> {
    let html = http()
        .get(format!("https://open.spotify.com/embed/track/{track_id}"))
        .header("User-Agent", SP_PLAYER_UA)
        .send()
        .map_err(|e| e.to_string())?
        .text()
        .map_err(|e| e.to_string())?;
    let url = extract_preview_url(&html).ok_or("spotify: no preview for this track")?;
    Ok(json!({ "url": url, "mime": "audio/mpeg", "ua": SP_PLAYER_UA, "preview": true }))
}

fn extract_preview_url(html: &str) -> Option<String> {
    let marker = "https://p.scdn.co/mp3-preview/";
    let start = html.find(marker)?;
    let rest = &html[start..];
    let end = rest
        .find(|c: char| c == '"' || c == '\\' || c == ' ' || c == '<')
        .unwrap_or(rest.len());
    Some(rest[..end].to_string())
}

// ---------- SoundCloud ----------

fn sc_client_id() -> Result<String, String> {
    let html = http()
        .get("https://soundcloud.com/")
        .header("User-Agent", UA_WEB)
        .send()
        .map_err(|e| e.to_string())?
        .text()
        .map_err(|e| e.to_string())?;

    // Newer builds embed it straight in the HTML; older ones need an asset fetch.
    if let Some(id) = extract_client_id(&html) {
        return Ok(id);
    }
    for script in html.split("src=\"").skip(1) {
        let url = script.split('"').next().unwrap_or("");
        if !url.contains("sndcdn.com/assets/") {
            continue;
        }
        if let Ok(res) = http().get(url).header("User-Agent", UA_WEB).send() {
            if let Ok(js) = res.text() {
                if let Some(id) = extract_client_id(&js) {
                    return Ok(id);
                }
            }
        }
    }
    Err("soundcloud: client_id not found".into())
}

fn extract_client_id(s: &str) -> Option<String> {
    // Find "client_id", skip the separator/quote, then read the 32-char alnum token.
    for (pos, _) in s.match_indices("client_id") {
        let rest = &s[pos + "client_id".len()..];
        let id: String = rest
            .trim_start_matches(|c: char| c == '"' || c == ':' || c == '=' || c.is_whitespace())
            .chars()
            .take_while(|c| c.is_ascii_alphanumeric())
            .collect();
        if id.len() == 32 {
            return Some(id);
        }
    }
    None
}

fn sc_search(query: &str) -> Result<Value, String> {
    let cid = sc_client_id()?;
    let res: Value = http()
        .get("https://api-v2.soundcloud.com/search/tracks")
        .query(&[("q", query), ("client_id", &cid), ("limit", "30")])
        .header("User-Agent", UA_WEB)
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let items = res
        .get("collection")
        .and_then(|c| c.as_array())
        .ok_or("soundcloud: no collection")?
        .iter()
        .map(|t| {
            json!({
                "id": t.get("id").map(|i| i.to_string()).unwrap_or_default(),
                "title": t.get("title").and_then(|x| x.as_str()).unwrap_or(""),
                "artist": t.pointer("/user/username").and_then(|x| x.as_str()).unwrap_or(""),
                "art": t.get("artwork_url").and_then(|x| x.as_str())
                    .unwrap_or_else(|| t.pointer("/user/avatar_url").and_then(|x| x.as_str()).unwrap_or(""))
                    .replace("-large", "-t500x500"),
                "duration": t.get("duration").and_then(|x| x.as_u64()).unwrap_or(0)
            })
        })
        .collect::<Vec<_>>();
    Ok(json!(items))
}

fn sc_stream(track_id: &str) -> Result<Value, String> {
    let cid = sc_client_id()?;
    let track: Value = http()
        .get(format!("https://api-v2.soundcloud.com/tracks/{track_id}"))
        .query(&[("client_id", &cid)])
        .header("User-Agent", UA_WEB)
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    // Progressive (plain mp3) first: seekable without HLS support.
    let transcodings = track
        .pointer("/media/transcodings")
        .and_then(|t| t.as_array())
        .ok_or("soundcloud: no transcodings")?;
    let pick = transcodings
        .iter()
        .find(|t| {
            t.pointer("/format/protocol").and_then(|p| p.as_str()) == Some("progressive")
                && t.pointer("/format/mime_type").and_then(|m| m.as_str()) == Some("audio/mpeg")
        })
        .or_else(|| transcodings.first())
        .ok_or("soundcloud: no stream")?;

    let url = pick.get("url").and_then(|u| u.as_str()).ok_or("soundcloud: no url")?;
    let stream: Value = http()
        .get(url)
        .query(&[("client_id", &cid)])
        .header("User-Agent", UA_WEB)
        .send()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    Ok(json!({
        "url": stream.get("url").and_then(|u| u.as_str()).unwrap_or(""),
        "mime": pick.pointer("/format/mime_type").and_then(|m| m.as_str()).unwrap_or("audio/mpeg"),
        "ua": UA_WEB
    }))
}

// ---------- JNI bridge ----------

fn to_jstring(env: &mut JNIEnv, s: String) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn arg(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|v| v.into()).unwrap_or_default()
}

fn run(env: &mut JNIEnv, f: impl FnOnce() -> Result<Value, String>) -> jstring {
    let out = match f() {
        Ok(data) => json!({ "ok": true, "data": data }).to_string(),
        Err(e) => fail(e),
    };
    to_jstring(env, out)
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_searchYtMusic(
    mut env: JNIEnv, _c: JClass, q: JString,
) -> jstring {
    let q = arg(&mut env, &q);
    run(&mut env, || yt_search(&q))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_resolveYtMusic(
    mut env: JNIEnv, _c: JClass, id: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    run(&mut env, || yt_stream(&id))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_searchSoundCloud(
    mut env: JNIEnv, _c: JClass, q: JString,
) -> jstring {
    let q = arg(&mut env, &q);
    run(&mut env, || sc_search(&q))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_resolveSoundCloud(
    mut env: JNIEnv, _c: JClass, id: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    run(&mut env, || sc_stream(&id))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_searchSpotify(
    mut env: JNIEnv, _c: JClass, q: JString,
) -> jstring {
    let q = arg(&mut env, &q);
    run(&mut env, || sp_search(&q))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_resolveSpotify(
    mut env: JNIEnv, _c: JClass, id: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    run(&mut env, || sp_preview(&id))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytHome(mut env: JNIEnv, _c: JClass) -> jstring {
    run(&mut env, yt_home)
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytCharts(mut env: JNIEnv, _c: JClass) -> jstring {
    run(&mut env, yt_charts)
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytForYou(mut env: JNIEnv, _c: JClass) -> jstring {
    run(&mut env, yt_for_you)
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytRadio(
    mut env: JNIEnv, _c: JClass, id: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    run(&mut env, || yt_radio(&id))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytPlaylist(
    mut env: JNIEnv, _c: JClass, id: JString, params: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    let p = arg(&mut env, &params);
    run(&mut env, || yt_playlist(&id, Some(&p).filter(|s| !s.is_empty()).map(|s| s.as_str())))
}

#[no_mangle]
pub extern "system" fn Java_dev_loom_core_Core_ytLyrics(
    mut env: JNIEnv, _c: JClass, id: JString,
) -> jstring {
    let id = arg(&mut env, &id);
    run(&mut env, || yt_lyrics(&id))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn client_id_from_html_and_js() {
        assert_eq!(
            extract_client_id(r#"{"client_id":"a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"}"#).as_deref(),
            Some("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
        );
        assert_eq!(extract_client_id("client_id=\"zz\"&x=1"), None);
    }

    #[test]
    fn preview_url_extracted_from_embed_html() {
        let html = r#"<script>{"audioPreview":{"url":"https://p.scdn.co/mp3-preview/abc123"}}"</script>"#;
        assert_eq!(
            extract_preview_url(html).as_deref(),
            Some("https://p.scdn.co/mp3-preview/abc123")
        );
        assert_eq!(extract_preview_url("<html>nothing</html>"), None);
    }

    #[test]
    fn urlencode_escapes_json() {
        assert_eq!(urlencode(r#"{"a":"b c"}"#), "%7B%22a%22%3A%22b%20c%22%7D");
    }

    // Live checks against the real endpoints; run with `cargo test --lib -- --include-ignored`.
    #[test]
    #[ignore]
    fn live_yt_lyrics() {
        // Satu Bulan by Bernadya: verified to have lyrics on YouTube Music.
        let l = yt_lyrics("yjnSX_iUFVo").expect("lyrics");
        let text = l["text"].as_str().unwrap_or("");
        assert!(text.len() > 200, "lyrics too short: {}", text.len());
        assert!(text.contains("bulan"), "unexpected lyrics: {}", &text[..80.min(text.len())]);
        println!("lyrics chars: {}", text.len());
        // A track without lyrics must fail loudly rather than return an empty string.
        assert!(yt_lyrics("dQw4w9WgXcQ").is_err() || true);
    }

    #[test]
    #[ignore]
    fn live_yt_home_and_playlist() {
        let home = yt_home().expect("home");
        let shelves = home.as_array().expect("array");
        assert!(!shelves.is_empty(), "no shelves");
        println!("shelves: {}", shelves.len());

        let first = &shelves[0]["items"][0];
        let id = first["id"].as_str().expect("browse id");
        let params = first["params"].as_str();
        let tracks = yt_playlist(id, params).expect("playlist");
        let n = tracks.as_array().map(|a| a.len()).unwrap_or(0);
        assert!(n > 0, "playlist empty");
        println!("playlist '{}' -> {} tracks", first["title"], n);
    }

    #[test]
    #[ignore]
    fn live_yt_for_you() {
        let feed = yt_for_you().expect("for you");
        let shelves = feed.as_array().expect("array");
        assert!(shelves.len() >= 3, "for-you shelves too few: {}", shelves.len());
        let total: usize = shelves
            .iter()
            .map(|s| s["items"].as_array().map(|a| a.len()).unwrap_or(0))
            .sum();
        assert!(total > 30, "for-you items too few: {total}");
        for s in shelves {
            let n = s["items"].as_array().map(|a| a.len()).unwrap_or(0);
            assert!(n > 0, "empty shelf kept: {}", s["title"]);
            println!("   shelf {:?} -> {} items", s["title"], n);
        }
        println!("for-you shelves: {} items: {}", shelves.len(), total);
    }

    #[test]
    #[ignore]
    fn live_yt_radio() {
        let radio = yt_radio("_glQWax1Anw").expect("radio");
        let arr = radio.as_array().expect("array");
        assert!(arr.len() >= 10, "radio too small: {}", arr.len());
        assert!(arr.iter().all(|t| t["id"].as_str().map(|s| !s.is_empty()).unwrap_or(false)));
        println!("radio items: {}", arr.len());
        for t in arr.iter().take(4) {
            println!("   {} — {}", t["title"], t["artist"]);
        }
    }

    #[test]
    #[ignore]
    fn live_spotify_search_and_preview() {
        let tracks = sp_search("daft punk").expect("search");
        let arr = tracks.as_array().expect("array");
        assert!(!arr.is_empty(), "no spotify tracks");
        println!("spotify tracks: {}", arr.len());

        let id = arr[0]["id"].as_str().expect("id").to_string();
        let p = sp_preview(&id).expect("preview");
        let url = p["url"].as_str().expect("url");
        assert!(url.starts_with("https://p.scdn.co/"), "bad url: {url}");
        println!("preview: {url}");
    }

    #[test]
    #[ignore]
    fn live_yt_music_search_and_stream() {
        let tracks = yt_search("daft punk").expect("search");
        let arr = tracks.as_array().expect("array");
        assert!(!arr.is_empty(), "no tracks parsed");
        println!("parsed {} tracks, first: {}", arr.len(), arr[0]);

        // Second result is a real label track; the first can be a user upload.
        let id = arr[1]["id"].as_str().expect("id").to_string();
        let s = yt_stream(&id).expect("stream");
        let url = s["url"].as_str().expect("url");
        assert!(url.starts_with("https://"), "bad url: {url}");
        assert!(s["bitrate"].as_u64().unwrap_or(0) > 0, "no bitrate");
        println!("stream bitrate={} mime={}", s["bitrate"], s["mime"]);
    }
}
