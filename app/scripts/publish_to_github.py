#!/usr/bin/env python3
import os
import sys
import json
import base64
import subprocess
import urllib.request
import urllib.error

TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO_OWNER = "Bakhodirov-Doniyor-dev"
REPO_NAME = "super-terminal"
API_BASE = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}"

def run_cmd(cmd):
    print(f"Running: {cmd}")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error: {res.stderr}")
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def make_request(url, method="GET", data=None, headers=None, content_type="application/json"):
    req_headers = {
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "SuperTerminal-AutoPublisher"
    }
    if headers:
        req_headers.update(headers)
    
    body = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body = json.dumps(data).encode("utf-8")
            req_headers["Content-Type"] = "application/json"
        elif isinstance(data, bytes):
            body = data
            if content_type:
                req_headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            resp_data = resp.read()
            if resp.headers.get_content_type() == "application/json":
                return resp.status, json.loads(resp_data.decode("utf-8"))
            return resp.status, resp_data
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="ignore")
        print(f"HTTPError {e.code} for {url}: {err_body}")
        try:
            return e.code, json.loads(err_body)
        except Exception:
            return e.code, {"error": err_body}

def build_apk():
    print("Building Debug APK via Gradle...")
    ret, out, err = run_cmd("gradle assembleDebug")
    if ret != 0:
        print(f"Gradle build failed!\n{err}")
        return None
    apk_candidates = [
        "app/build/outputs/apk/debug/app-debug.apk",
        "build/outputs/apk/debug/app-debug.apk"
    ]
    for c in apk_candidates:
        if os.path.exists(c):
            print(f"Found APK: {c} ({os.path.getsize(c)} bytes)")
            return c
    print("APK not found after build!")
    return None

def update_version_json(version_code, version_name, apk_download_url, changelog):
    print("Updating version.json on GitHub...")
    date_today = "2026-09-25"
    version_data = {
        "latestVersionCode": version_code,
        "latestVersionName": version_name,
        "downloadUrl": apk_download_url,
        "changelog": changelog,
        "releaseDate": date_today,
        "mandatory": False
    }
    content_str = json.dumps(version_data, indent=2, ensure_ascii=False)
    content_b64 = base64.b64encode(content_str.encode("utf-8")).decode("utf-8")
    
    # Check if file exists to get sha
    url = f"{API_BASE}/contents/version.json"
    status, res = make_request(url)
    sha = None
    if status == 200 and "sha" in res:
        sha = res["sha"]
        
    payload = {
        "message": f"Release v{version_name} (Build {version_code})",
        "content": content_b64,
        "branch": "main"
    }
    if sha:
        payload["sha"] = sha
        
    p_status, p_res = make_request(url, method="PUT", data=payload)
    if p_status in (200, 201):
        print("version.json successfully committed to main branch!")
        return True
    else:
        print(f"Failed to update version.json: {p_res}")
        return False

def publish_release(version_code, version_name, changelog):
    tag_name = f"v{version_name}"
    release_name = f"Super Terminal v{version_name}"
    
    # 1. Build APK
    apk_path = build_apk()
    if not apk_path:
        sys.exit(1)
        
    # 2. Check if release already exists
    status, rel_check = make_request(f"{API_BASE}/releases/tags/{tag_name}")
    release_id = None
    upload_url_template = None
    
    if status == 200:
        release_id = rel_check["id"]
        upload_url_template = rel_check["upload_url"]
        print(f"Existing release found: ID {release_id}")
    else:
        print(f"Creating new GitHub Release: {tag_name}...")
        rel_payload = {
            "tag_name": tag_name,
            "target_commitish": "main",
            "name": release_name,
            "body": changelog,
            "draft": False,
            "prerelease": False
        }
        create_status, create_res = make_request(f"{API_BASE}/releases", method="POST", data=rel_payload)
        if create_status not in (200, 201):
            print(f"Failed to create release: {create_res}")
            sys.exit(1)
        release_id = create_res["id"]
        upload_url_template = create_res["upload_url"]
        print(f"Created release ID: {release_id}")
        
    # 3. Upload APK asset
    apk_filename = f"SuperTerminal-v{version_name}.apk"
    upload_url = upload_url_template.split("{")[0] + f"?name={apk_filename}"
    
    print(f"Uploading APK asset {apk_filename}...")
    with open(apk_path, "rb") as f:
        apk_bytes = f.read()
        
    up_status, up_res = make_request(
        upload_url,
        method="POST",
        data=apk_bytes,
        content_type="application/vnd.android.package-archive"
    )
    
    download_url = None
    if up_status in (200, 201):
        download_url = up_res.get("browser_download_url")
        print(f"APK Asset uploaded successfully! Direct URL: {download_url}")
    else:
        # Check if asset already exists
        print("Checking assets in release...")
        a_status, a_res = make_request(f"{API_BASE}/releases/{release_id}/assets")
        if a_status == 200:
            for asset in a_res:
                if asset["name"] == apk_filename:
                    download_url = asset["browser_download_url"]
                    print(f"Found existing asset download URL: {download_url}")
                    break
    
    if not download_url:
        download_url = f"https://github.com/{REPO_OWNER}/{REPO_NAME}/releases/download/{tag_name}/{apk_filename}"
        
    # 4. Update version.json on main
    update_version_json(version_code, version_name, download_url, changelog)
    
    print("\n" + "="*60)
    print("SUCCESS! Release published successfully!")
    print(f"Release: {tag_name}")
    print(f"APK Download: {download_url}")
    print(f"Metadata URL: https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/main/version.json")
    print("="*60 + "\n")

if __name__ == "__main__":
    v_code = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    v_name = sys.argv[2] if len(sys.argv) > 2 else "1.0"
    v_changelog = sys.argv[3] if len(sys.argv) > 3 else "Initial release with full ADB Super Terminal suite"
    publish_release(v_code, v_name, v_changelog)
