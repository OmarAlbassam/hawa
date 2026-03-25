from utils.preprocessing import clean_text


def test_strips_urls():
    text = "Check this out https://example.com great product"
    assert clean_text(text) == "Check this out great product"


def test_strips_mentions():
    text = "@user123 this product is amazing"
    assert clean_text(text) == "this product is amazing"


def test_normalizes_whitespace():
    text = "too   much    space   here"
    assert clean_text(text) == "too much space here"


def test_truncates_long_text():
    text = "a" * 3000
    result = clean_text(text, max_length=100)
    assert len(result) == 100


def test_strips_leading_trailing():
    text = "  hello world  "
    assert clean_text(text) == "hello world"


def test_combined_cleaning():
    text = "  @bot Check https://t.co/abc   great   delivery!  "
    assert clean_text(text) == "Check great delivery!"


def test_empty_string():
    assert clean_text("") == ""
