#!/usr/bin/env python3
"""Collect separately stored, freely licensed raster references from Wikimedia Commons."""

from __future__ import annotations

import csv
import html
import json
import re
import shutil
import time
import unicodedata
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

REPOSITORY = "https://github.com/AngmarX-dev/DeadMan-Draw"
API_URL = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = (
    "DeadManDraw-FutureArtCollector/1.0 "
    "(GitHub Actions; " + REPOSITORY + ")"
)
OUTPUT = Path("art-library/not-integrated")
IMAGES = OUTPUT / "images"
TEMP_IMAGES = OUTPUT / ".download-staging"
MAX_BYTES_PER_IMAGE = 6 * 1024 * 1024
MAX_PER_TOPIC = 2
MINIMUM_IMAGES = 5

TOPICS = [
    "pirate ship sailing",
    "treasure chest gold",
    "antique nautical map",
    "nautical compass",
    "kraken sea monster",
    "pirate skull flag",
    "tropical treasure island",
    "sailing ship ocean storm",
    "nautical anchor rope",
    "old ship cannon",
]

MANIFEST_FIELDS = [
    "filename",
    "topic",
    "commons_title",
    "commons_page_url",
    "original_file_url",
    "license",
    "license_url",
    "artist",
    "credit",
]


def request_json(url: str) -> dict:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        raw = response.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024:
        raise RuntimeError("Wikimedia API response exceeded the safety limit.")
    return json.loads(raw.decode("utf-8"))


def plain_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    value = re.sub(r"<[^>]*>", " ", value)
    value = html.unescape(value)
    return " ".join(value.split()).strip()


def metadata_value(metadata: dict, key: str) -> str:
    item = metadata.get(key, {})
    if isinstance(item, dict):
        return plain_text(item.get("value", ""))
    return plain_text(item)


def allowed_license(name: str) -> bool:
    normalized = " ".join(name.upper().replace("_", " ").split())
    if "PUBLIC DOMAIN" in normalized or normalized == "PD":
        return True
    if normalized.startswith("CC0"):
        return True
    # Explicitly allow attribution and attribution-share-alike licenses.
    # Non-commercial and no-derivatives licenses do not match these patterns.
    return bool(
        re.fullmatch(r"CC BY(?:-SA)? (?:2\.0|2\.5|3\.0|4\.0)", normalized)
    )


def safe_stem(title: str, page_id: int, topic: str) -> str:
    raw = title.removeprefix("File:")
    normalized = unicodedata.normalize("NFKD", raw)
    ascii_name = normalized.encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", ascii_name).strip("-").lower()
    topic_slug = re.sub(r"[^a-z0-9]+", "-", topic.lower()).strip("-")
    slug = slug[:68].strip("-") or "commons-art"
    return f"{topic_slug[:24]}-{slug}-{page_id}"


def image_extension(payload: bytes) -> str | None:
    if payload.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if payload.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if len(payload) >= 12 and payload[:4] == b"RIFF" and payload[8:12] == b"WEBP":
        return ".webp"
    return None


def fetch_bytes(url: str) -> bytes:
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != "upload.wikimedia.org":
        raise ValueError("Refusing a download URL outside upload.wikimedia.org.")
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=45) as response:
        length = response.headers.get("Content-Length")
        if length and int(length) > MAX_BYTES_PER_IMAGE:
            raise ValueError("Image is larger than the configured size limit.")
        data = response.read(MAX_BYTES_PER_IMAGE + 1)
    if len(data) > MAX_BYTES_PER_IMAGE:
        raise ValueError("Image download exceeded the configured size limit.")
    if image_extension(data) is None:
        raise ValueError("Downloaded response was not a supported PNG, JPEG, or WebP image.")
    return data


def search_topic(topic: str) -> list[dict]:
    params = {
        "action": "query",
        "generator": "search",
        "gsrsearch": topic,
        "gsrnamespace": "6",
        "gsrlimit": "35",
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
        "iiurlwidth": "1200",
        "format": "json",
        "formatversion": "2",
    }
    response = request_json(API_URL + "?" + urlencode(params))
    pages = response.get("query", {}).get("pages", [])
    if isinstance(pages, dict):
        pages = list(pages.values())
    return pages


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if TEMP_IMAGES.exists():
        shutil.rmtree(TEMP_IMAGES)
    TEMP_IMAGES.mkdir(parents=True)

    seen_page_ids: set[int] = set()
    downloaded: list[dict[str, str]] = []
    errors: list[str] = []

    for topic in TOPICS:
        topic_count = 0
        print(f"Searching Wikimedia Commons: {topic}")
        try:
            pages = search_topic(topic)
        except (HTTPError, URLError, TimeoutError, json.JSONDecodeError, RuntimeError) as error:
            errors.append(f"Search failed for {topic!r}: {error}")
            print(f"  Search error: {error}")
            continue

        for page in pages:
            if topic_count >= MAX_PER_TOPIC:
                break
            page_id = page.get("pageid")
            if not isinstance(page_id, int) or page_id in seen_page_ids:
                continue

            info_list = page.get("imageinfo", [])
            if not info_list:
                continue
            info = info_list[0]
            mime = info.get("mime", "")
            if mime not in {"image/jpeg", "image/png", "image/webp"}:
                continue

            metadata = info.get("extmetadata", {})
            license_name = metadata_value(metadata, "LicenseShortName")
            license_url = metadata_value(metadata, "LicenseUrl")
            if not allowed_license(license_name):
                continue

            thumb_url = info.get("thumburl") or info.get("url")
            original_url = info.get("url", "")
            if not isinstance(thumb_url, str) or not thumb_url:
                continue

            try:
                payload = fetch_bytes(thumb_url)
            except (HTTPError, URLError, TimeoutError, ValueError, OSError) as error:
                errors.append(f"Skipped {page.get('title', page_id)!r}: {error}")
                print(f"  Skipped {page.get('title', page_id)!r}: {error}")
                continue

            extension = image_extension(payload)
            assert extension is not None
            filename = safe_stem(str(page.get("title", "commons-art")), page_id, topic) + extension
            (TEMP_IMAGES / filename).write_bytes(payload)

            description_url = info.get("descriptionurl") or (
                "https://commons.wikimedia.org/wiki/"
                + str(page.get("title", "")).replace(" ", "_")
            )
            downloaded.append({
                "filename": filename,
                "topic": topic,
                "commons_title": str(page.get("title", "")),
                "commons_page_url": str(description_url),
                "original_file_url": str(original_url),
                "license": license_name,
                "license_url": license_url,
                "artist": metadata_value(metadata, "Artist"),
                "credit": metadata_value(metadata, "Credit"),
            })
            seen_page_ids.add(page_id)
            topic_count += 1
            print(f"  Saved {filename} ({len(payload)} bytes; {license_name})")

        time.sleep(0.35)

    if len(downloaded) < MINIMUM_IMAGES:
        shutil.rmtree(TEMP_IMAGES, ignore_errors=True)
        detail = "\n".join(errors[-10:])
        raise RuntimeError(
            f"Only collected {len(downloaded)} images; at least {MINIMUM_IMAGES} are "
            "required before replacing the existing library. "
            + (f"Recent errors:\n{detail}" if detail else "")
        )

    # Replace only this isolated art-reference folder after the collection succeeded.
    IMAGES.mkdir(parents=True, exist_ok=True)
    for old_file in IMAGES.iterdir():
        if old_file.is_file() and old_file.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
            old_file.unlink()
    for new_file in TEMP_IMAGES.iterdir():
        shutil.move(str(new_file), str(IMAGES / new_file.name))
    shutil.rmtree(TEMP_IMAGES, ignore_errors=True)

    manifest_path = OUTPUT / "manifest.csv"
    with manifest_path.open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=MANIFEST_FIELDS)
        writer.writeheader()
        writer.writerows(downloaded)

    print(f"Collected {len(downloaded)} images in {IMAGES}.")
    print(f"Attribution and source links saved to {manifest_path}.")
    if errors:
        print(f"Non-fatal skipped items: {len(errors)}")


if __name__ == "__main__":
    main()
