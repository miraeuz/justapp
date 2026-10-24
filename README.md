# Attest Kit (Android)

1. Android Studio (Koala+) → Open → this folder. Let Gradle sync (needs internet: JitPack for TDLib).
2. Get `api_id` / `api_hash` at https://my.telegram.org → API development tools.
3. Run on a phone (Android 7+). Log in with your own Telegram account (code + 2FA if enabled).

## Tabs
- **Yig‘ish**: for each variant sends /start → taps the subject button → taps "Informatika {n}-variant" → answers each quiz
  (option 1, to read the correct index) → saves question/options/correct/explanation → taps "Tushunarli".
  Every bot message is also kept in `raw_messages`, so nothing is lost if a message format differs.
- **Raqamlar**: sends start…end (step) to any bot; saved bot chips; optional wait-for-reply; auto-handles Telegram 429 limits.
- **Baza**: preview + export `questions.db` (SQLite) or JSON.

SQLite tables: `questions(bot, subject, variant, idx, kind, question, options[json], correct, explanation)` and `raw_messages`.
