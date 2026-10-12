# Future Art Library — Not Integrated

This directory is a **separate reference library** for possible future artwork for Dead Man's Draw.

- Images are collected from Wikimedia Commons by the GitHub Actions workflow.
- They are deliberately stored outside `app/src/main/`, so Android does not package them as app assets and the game does not use them.
- `images/` contains the downloaded previews.
- `manifest.csv` records each image's Commons page, source URL, author/credit, license, and license URL.

Review each image and its license before using it in the game. Some files require attribution and/or share-alike licensing. The manifest is the reference for those obligations.

Run **Actions → Collect Future Art Library → Run workflow** to refresh this collection. The collector permits only raster images with recognized free licenses (CC0, public domain, CC BY, or CC BY-SA); it skips unknown and restrictive licenses.
