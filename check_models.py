"""
Chay file nay de xem models nao available voi API key cua ban:
  python check_models.py
"""
import os
from dotenv import load_dotenv
from google import genai

load_dotenv()

api_key = os.getenv("GEMINI_API_KEY", "")
if not api_key:
    print("Khong tim thay GEMINI_API_KEY trong .env")
    raise SystemExit(1)

client = genai.Client(api_key=api_key)

print("Danh sach models ho tro generateContent:\n")
for m in client.models.list():
    name = str(getattr(m, "name", ""))
    if not name:
        continue
    short = name.replace("models/", "")
    if "gemini" in short.lower():
        print(f"  - {short}")

print("\nCopy ten model vao .env: GEMINI_MODEL=<ten model>")
