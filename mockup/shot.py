from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/bin/chromium", args=["--no-sandbox"])
    pg = b.new_page(viewport={"width": 1360, "height": 980}, device_scale_factor=2)
    pg.goto("file:///home/ubuntu/island/mockup/preview.html")
    pg.wait_for_timeout(700)
    pg.screenshot(path="/home/ubuntu/island/mockup/ada-onizleme.png")
    b.close()
print("ok")
