package com.hawa.hawa_backend.dataset.dto;

import com.hawa.hawa_backend.enums.LanguageEnum;

public record ParsedPost(String text, String url, LanguageEnum language) {}
