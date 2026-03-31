"""
Config đơn giản - đọc từ .env hoặc environment variables
Không cần YAML file
"""
import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    def __init__(self, api_key=None, model=None):
        self._data = {
            'api.api_key':                    api_key or os.getenv("GEMINI_API_KEY", ""),
            'api.model_version':              model or os.getenv("GEMINI_MODEL", "gemini-flash-latest"),
            'prompts.system_prompt_path':     os.path.join(os.path.dirname(__file__), "system_prompt_vn.txt"),
            'api.generation.temperature':     0.0,
            'api.generation.top_p':           0.9,
            'api.generation.top_k':           40,
            'api.retry.max_retries':          3,
            'api.retry.base_delay':           1.0,
            'api.retry.max_delay':            30.0,
            'preprocessing.max_image_size':   1600,
            'validation.strict_mode':         False,
            'normalization.auto_normalize':   True,
        }

    def get(self, key, default=None):
        return self._data.get(key, default)

    def validate(self):
        return bool(self._data.get('api.api_key'))
