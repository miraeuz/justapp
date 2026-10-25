"""Builds the compact app database (assets/bank.db) from Mirae_Questions_v3.sqlite3."""
import sqlite3, re, json, sys, os
SRC, OUT = sys.argv[1], sys.argv[2]
def n(s): return s.lower().replace("ʻ","'").replace("’","'").replace("‘","'").replace("`","'")
MAP = {"Tarmoq":"Tarmoq va Internet","SQL va Access":"Ma'lumotlar bazasi","Grafika":"Grafika va multimedia",
 "Multimedia":"Grafika va multimedia","Excel":"Excel","Algoritmlar":"Algoritm va dasturlash",
 "Axborotni kodlash":"Mantiq va sanoq sistemalari","Sanoq sistemalari":"Mantiq va sanoq sistemalari",
 "Mantiq":"Mantiq va sanoq sistemalari","Veb texnologiyalar":"HTML va veb","Taqdimot":"Office va taqdimot",
 "Office":"Office va taqdimot","Pedagogika":"Pedagogika va psixologiya","Axborot xavfsizligi":"Xavfsizlik va huquq"}
RULES = [
 ("Pedagogika va psixologiya", r"o'qituvch|o'quvchi|pedagog|didakt|tarbiya|psixolog|piaje|vygotsk|temperament|reflek|kompetensiya|inklyuziv|steam|ta'lim|baholash|shaxs|dars\b|darsning|metod|o'quv|o'qitish|test topshiriq|reliab|flipped|to'garak|nostandart|tafakkur|insert"),
 ("Excel", r"excel|yacheyk|=sum|=if|=count|=average|#name|#value|#div|formula|diagramma|gistogramma"),
 ("Xavfsizlik va huquq", r"virus|parol|fishing|soxta|shifr|xavfsiz|mualliflik|zararli|antivirus|hujum|autentifik|troyan|spam"),
 ("Office va taqdimot", r"\bword\b|powerpoint|taqdimot|slayd|animatsiya|ctrl\s*\+|matn muharrir|abzats|shrift"),
 ("HTML va veb", r"html|css|\bdiv\b|margin|float|padding|<[a-z]+>|veb|brauzer|sayt|teg\b"),
 ("Ma'lumotlar bazasi", r"\bsql\b|access|ma'lumotlar baza|maydon|select\b|so'rov|yozuv"),
 ("Tarmoq va Internet", r"tarmoq|\bip\b|ipv4|ipv6|router|marshrutizator|topologiya|http|\bdns\b|protokol|internet|wi-?fi|\burl\b|kommutator|\bhab\b|mask"),
 ("Grafika va multimedia", r"rgb|piksel|tasvir|\brang|raster|vektor|dyuym|crop|qirqish|cmyk|photoshop|grafik|audio|video|multimedia|bmp|jpeg|png|gif"),
 ("Mantiq va sanoq sistemalari", r"mantiq|\bnand\b|\bnor\b|\band\b|\bor\b|\bnot\b|ikkilik|sanoq|\bbit\b|bayt|kodlash|o'nlik|sakkizlik|o'n oltilik|\bhex|\bascii|unicode"),
 ("Algoritm va dasturlash", r"python|c\+\+|javascript|scratch|sprayt|robot|toshbaqa|turtle|algoritm|sikl|o'zgaruvchi|funksiya|\bkod|dastur|massiv|\bdict|lug'at|\blist\b|tuple|ko'prik|fonar|print\(|cout|labirint|blok"),
 ("Kompyuter va OS", r"\bram\b|bios|protsessor|xotira|operatsion|defragment|monitor|qurilma|windows|fayl|papka|kompyuter|klaviatura|sichqoncha|printer|skaner|\brom\b|\bssd\b|\bhdd\b"),
]
def classify(t):
    t = n(t)
    for name, rx in RULES:
        if re.search(rx, t): return name
    return "Umumiy informatika"
def clean_q(q):
    q = re.sub(r"^\s*\d+\s*(-\s*savol)?\s*[.)]\s*", "", q)
    q = re.sub(r"```[a-zA-Z+#]*\n?", "\n", q).replace("```", "\n")
    q = q.replace("`", "")
    return re.sub(r"[ \t]+\n", "\n", q).strip()
def split_expl(e):
    e = (e or "").strip(); method = mistake = ""
    m = re.search(r"Yechim:\s*(.*?)(?:\n\s*\n|\Z)", e, re.S)
    k = re.search(r"Ko['ʻ’‘]p uchraydigan xato:\s*(.*)$", e, re.S|re.I)
    if m: method = m.group(1).strip()
    if k: mistake = k.group(1).strip()
    if m or k: return "", method, mistake
    e = re.sub(r"\s*To['ʻ’‘]g['ʻ’‘]ri javob:.*$", "", e, flags=re.S).strip()
    return e, "", ""
src = sqlite3.connect(SRC)
if os.path.exists(OUT): os.remove(OUT)
out = sqlite3.connect(OUT)
out.execute("""CREATE TABLE questions(id INTEGER PRIMARY KEY, src TEXT, variant INTEGER, idx INTEGER, topic TEXT,
  diff TEXT, verified INTEGER, q TEXT, opts TEXT, correct INTEGER, expl TEXT, method TEXT, mistake TEXT, code INTEGER)""")
out.execute("CREATE TABLE notes(k TEXT PRIMARY KEY, v TEXT)")
rows = src.execute("""SELECT q.id,q.bot,q.variant,q.idx,q.question,q.options,q.correct,q.explanation,r.topic,r.difficulty,r.status,
  d.method,d.common_mistake FROM questions q JOIN question_review r ON r.question_id=q.id
  LEFT JOIN question_explanation_detail d ON d.question_id=q.id WHERE r.status!='needs_code_format_review'""").fetchall()
skipped = 0; stats = {}
for id_,bot,var,idx,q,opts,corr,expl,topic,diff,status,dm,dc in rows:
    o = json.loads(opts)
    if len(o)!=4 or corr not in (0,1,2,3) or not q.strip(): skipped += 1; continue
    q2 = clean_q(q)
    t = MAP.get(topic) if topic!="source_mixed" else classify(q2)
    e, method, mistake = split_expl(expl)
    if dm: method = dm
    if dc: mistake = dc
    s = {"Yangi_Attestatsiya_TestlariBot":"exam","Mirae_Original_Practice_v2":"m2","Mirae_Original_Practice_v3":"m3"}[bot]
    ver = 1 if status in ("reviewed_new","source_code_repaired_verified") else 0
    out.execute("INSERT INTO questions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
      (id_,s,var,idx,t,diff if diff!="unknown" else "",ver,q2,json.dumps(o,ensure_ascii=False),corr,e,method,mistake,1 if "\n" in q2 else 0))
    stats[t] = stats.get(t,0)+1
for k,v in src.execute("select key,value from bank_notes"): out.execute("INSERT INTO notes VALUES(?,?)",(k,v))
out.execute("CREATE INDEX i_topic ON questions(topic)"); out.execute("CREATE INDEX i_var ON questions(src,variant)")
out.execute("PRAGMA user_version=1"); out.commit(); out.execute("VACUUM"); out.close()
print("rows",len(rows),"skipped",skipped); print(json.dumps(stats,ensure_ascii=False,indent=1))
print("size",os.path.getsize(OUT))
