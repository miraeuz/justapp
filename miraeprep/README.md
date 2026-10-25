# Mirae Prep — Informatika attestatsiyasiga tayyorgarlik (Android)

Duolingo uslubidagi oflayn ilova: kunlik mashq, mavzular, takrorlash (spaced repetition), imtihon rejimi (50 ta attestatsiya varianti + 11 ta Mirae to‘plam), xatolar ustida ishlash, belgilangan savollar, qidiruv, XP / seriya / kunlik maqsad.

- Baza: `app/src/main/assets/bank.db` (2972 savol) — `tools/build_bank.py` yordamida `Mirae_Questions_v3.sqlite3` dan yaratilgan.
- Savollar mavzulari: yangi savollar uchun bazadagi teg, eski (manba) savollar uchun kalit so‘zlar bo‘yicha avtomatik tasnif.
- Javob kaliti holati: faqat `reviewed_new` va `source_code_repaired_verified` tasdiqlangan hisoblanadi; qolganlari darsda ogohlantirish bilan ko‘rsatiladi.
