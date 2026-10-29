#!/usr/bin/env python3
"""
Convert the Retro Blast weekly schedule PDF (a spreadsheet printed across pages)
into app/src/main/assets/retroblast.json.

Usage:  pip install pdfplumber
        python3 tools/retroblast_pdf_to_json.py schedule.pdf app/src/main/assets/retroblast.json

Layout assumptions (matches the 2026 Halloween sheet):
  pages 1-3  : rows 12:00am..5:10pm  -> [time + Mon, Tue] [Wed, Thu, Fri] [Sat, Sun]
  pages 5-7  : rows 5:35pm..11:47pm  -> same three column groups
Cell colours map to the legend printed on page 5.
If a future sheet is laid out differently, adjust PAGES below.
"""
import json, re, sys
import pdfplumber

PDF = sys.argv[1] if len(sys.argv) > 1 else "schedule.pdf"
OUT = sys.argv[2] if len(sys.argv) > 2 else "retroblast.json"

# (page index, [day keys in column order])  first group = early rows, second = late rows
EARLY = [(0, ["mon", "tue"]), (1, ["wed", "thu", "fri"]), (2, ["sat", "sun"])]
LATE = [(4, ["mon", "tue"]), (5, ["wed", "thu", "fri"]), (6, ["sat", "sun"])]

COLORS = {
    (0.0, 1.0, 0.0): "Miguzi",
    (0.0, 0.0, 0.0): "Adult Swim",
    (0.0, 0.0, 1.0): "Fridays",
    (0.878, 0.4, 0.4): "Rocky Horror",
    (1.0, 0.6, 0.0): "Cartoon Films",
    (0.6, 0.6, 0.6): "Toonami",
    (1.0, 0.0, 1.0): "Cartoon Network",
    (0.043, 0.325, 0.58): "Boomerang",
    (0.47, 0.247, 0.016): "Cabin Fever",
}

FIX = {
    "Metalocyalpse": "Metalocalypse", "Jusstice Leagye": "Justice League",
    "Dexters Lab": "Dexter's Lab", "BoboBoBoBoBoBo": "Bobobo-bo Bo-bobo",
    "Naruto/ Shippuden": "Naruto/Shippuden", "Hi Hi Puffy Ami Yumi": "Hi Hi Puffy AmiYumi",
}

LOOKUP = {
    "ATHF": "Aqua Teen Hunger Force", "Aqua Teen": "Aqua Teen Hunger Force",
    "PPG": "The Powerpuff Girls", "KND": "Codename: Kids Next Door",
    "Billy and Mandy": "The Grim Adventures of Billy & Mandy",
    "Fosters": "Foster's Home for Imaginary Friends", "Dexter's Lab": "Dexter's Laboratory",
    "DBZ": "Dragon Ball Z", "FMA Brotherhood": "Fullmetal Alchemist: Brotherhood",
    "TMNT (2003)": "Teenage Mutant Ninja Turtles", "Batman TAS": "Batman: The Animated Series",
    "X-Men TAS": "X-Men: The Animated Series", "YuYu": "Yu Yu Hakusho",
    "Naruto/Shippuden": "Naruto Shippuden", "Pokemon Advanced": "Pokémon",
    "Yu-Gi-Oh! DM": "Yu-Gi-Oh!", "Juniper Lee": "The Life and Times of Juniper Lee",
    "Star Wars The Clone Wars": "Star Wars: The Clone Wars", "Ed Edd n Eddy": "Ed, Edd n Eddy",
    "Shin Chan": "Crayon Shin-chan", "Ghost in the Shell SAC": "Ghost in the Shell: Stand Alone Complex",
    "Bleach TYBW": "Bleach: Thousand-Year Blood War", "Space Ghost": "Space Ghost Coast to Coast",
    "Eric Andre": "The Eric Andre Show", "Tim and Eric": "Tim and Eric Awesome Show, Great Job!",
    "Harvey Birdman": "Harvey Birdman, Attorney at Law", "On Cinema": "On Cinema at the Cinema",
    "Power Rangers": "Mighty Morphin Power Rangers", "Courage": "Courage the Cowardly Dog",
    "Gundam Wing": "Mobile Suit Gundam Wing", "Robot Boy": "Robotboy",
    "Zatch Bell": "Zatch Bell!", "Maul Shadow Lord": "Star Wars: Maul - Shadow Lord",
    "Mucha Lucha": "Mucha Lucha!", "Avatar TLA": "Avatar: The Last Airbender",
    "Check it Out": "Check It Out! with Dr. Steve Brule", "Hellsing": "Hellsing Ultimate",
}
NO_LOOKUP = ("Boomerang", "Retro Blast Halloween", "Fridays Halloween",
             "Adult Halloween Specials", "Horror Movie", "Cartoon Theater")

BOOMERANG_NOTE = ("Hour 1: Looney Tunes, The Flintstones, Smurfs  |  "
                  "Hour 2: Yogi Bear, Huckleberry Hound, Hong Kong Phooey, The Jetsons  |  "
                  "Hour 3: Birdman and the Galaxy Trio, Jonny Quest, Josie and the Pussycats")
THEATER_NOTE = "Randomly curated. Movies are announced on Discord."

# Dated specials (typed from the sheet's last pages). Keys are dates in the channel's timezone.
DATED = {
    "halloween": {
        "2026-10-16": "7:00 Foster's: Bloooo  *  7:30 KND: Operation B.U.L.L.I.E.S. / F.I.S.H.Y.  *  8:00 KND: Operation B.R.E.A.K.U.P. / S.A.F.A.R.I.  *  8:30 Billy & Mandy: Prank Call of Cthulhu  *  9:00 The Haunted Pumpkin of Sleepy Hollow  *  10:00 Billy & Mandy: Jacked Up Halloween  *  10:30 Hi Hi Puffy AmiYumi: Domo / Yumi Goes Solo / Cat Feud",
        "2026-10-19": "Johnny Bravo  *  Courage  *  Foster's: Nightmare on Wilson Way  *  Ed, Edd n Eddy's Jacked Up Halloween  *  Pokémon",
        "2026-10-21": "Hocus Pocus  *  Halloweentown",
        "2026-10-23": "7:00 Foster's: Nightmare on Wilson Way  *  7:30 Scary Godmother Halloween Spectacular  *  8:30 Billy & Mandy (TBA)  *  9:00 Billy & Mandy: Jacked Up Halloween  *  9:30 Scary Godmother: Revenge of Jimmy  *  10:30 Camp Lazlo: Hallobeanies",
        "2026-10-26": "Scooby-Doo  *  Courage  *  Little Shop of Horrors  *  Johnny Bravo",
        "2026-10-28": "7:00 Hamtaro  *  7:30 Teen Titans: Haunted  *  8:00 Ben 10: Ghostfreaked Out  *  8:30 Ben 10: The Return  *  9:00 Ben 10: Be Afraid of the Dark",
        "2026-10-29": "7:00 Class of 3000: The Hunt for Red Blobtober  *  7:30 Batman TAS: Nothing to Fear  *  8:00 Powerpuff Girls: Speed Demon / Tough Love  *  8:30 KND: Operation T.R.I.C.K.Y.  *  8:45 Scooby-Doo and the Alien Invaders",
        "2026-10-30": "7:00 Sunny Bridges: From Bankhead to Buckhead  *  7:15 Billy & Mandy  *  7:30 Jacked Up Halloween  *  8:00 Boo-Ha-Ha  *  8:30 Courage  *  9:00 Scooby-Doo and the Witch's Ghost  *  10:10 Camp Lazlo Halloween  *  10:30 Juniper Lee: It's the Great Pumpkin Juniper Lee",
        "2026-10-31": "Special Halloween schedule on Halloween night",
    },
    "theater": {
        "2026-10-17": "Scooby-Doo and the Cyberchase",
        "2026-10-18": "The Batman vs. Dracula  *  Batman TAS: Nothing to Fear",
        "2026-10-24": "Kiki's Delivery Service",
        "2026-10-25": "Scooby-Doo on Zombie Island",
    },
    # Horror films air Saturday at midnight = the Sunday 12:00am slot
    "horror": {
        "2026-10-04": "Halloween (1978), hosted by Svengoolie",
        "2026-10-11": "Halloween III: Season of the Witch, hosted by Svengoolie",
        "2026-10-18": "The Texas Chainsaw Massacre, hosted by Elvira, Mistress of the Dark",
        "2026-10-25": "Scream, hosted by the cast of Scary Movie",
        "2026-11-01": "The Rocky Horror Picture Show, hosted by Meatloaf",
    },
}

def parse_time_col(words):
    out = []
    for w in words:
        m = re.fullmatch(r"(\d{1,2}):(\d{2})(?::\d{2})?\s*(am|pm)?", w["text"].strip(), re.I)
        if m and w["x0"] < 150:
            out.append((w["top"], int(m.group(1)), int(m.group(2)), (m.group(3) or "").lower()))
    return out

def infer_minutes(rows):
    prev = -1; res = []
    for top, h, m, ap in rows:
        base = (h % 12) * 60 + m
        cands = [base, base + 720]
        if ap == "am": cands = [base]
        elif ap == "pm": cands = [base + 720]
        pick = next((c for c in cands if c > prev), cands[-1])
        res.append((top, pick)); prev = pick
    return res

def clean(raw):
    t = raw.strip(); ep = ""
    m = re.search(r"[*\"“”]\s*NEW\s*([A-Za-z]+)\s*[*\"“”]", t)
    if m: ep = "New " + m.group(1).lower(); t = t.replace(m.group(0), "")
    m = re.search(r"\((Run [AB]|Season \d+)\)", t)
    if m: ep = (ep + " " + m.group(1)).strip(); t = t.replace(m.group(0), "")
    t = re.sub(r"\s+x2$", "", t.strip()); t = t.rstrip("*").strip()
    t = re.sub(r"\s+", " ", t)
    t = FIX.get(t, t)
    return t, ep

def color_at(rects, x, y):
    best = None
    for r in rects:
        c = r.get("non_stroking_color")
        if not c or len(c) != 3 or tuple(c) == (1.0, 1.0, 1.0): continue
        if r["x0"] - 1 <= x <= r["x1"] + 1 and r["top"] - 1 <= y <= r["bottom"] + 1: best = tuple(c)
    if best is None: return ""
    key = min(COLORS, key=lambda k: sum((a - b) ** 2 for a, b in zip(k, best)))
    return COLORS[key]

def main():
    pdf = pdfplumber.open(PDF)
    times = []
    for pg, _ in (EARLY[0], LATE[0]):
        w = pdf.pages[pg].extract_words(keep_blank_chars=True, x_tolerance=2, y_tolerance=2)
        times.append(infer_minutes(parse_time_col(w)))
    # continue am/pm inference across the two groups
    early, late = times
    late_fixed = []
    prev = early[-1][1]
    for top, mins in late:
        while mins <= prev: mins += 720
        late_fixed.append((top, mins)); prev = mins
    tops_early = [t for t, _ in early]; tops_late = [t for t, _ in late_fixed]
    mins_all = [m for _, m in early] + [m for _, m in late_fixed]

    anchors = {}  # day -> x anchor, from page header words of the early group
    for pg, days in EARLY:
        hdr = [w for w in pdf.pages[pg].extract_words(keep_blank_chars=True, x_tolerance=2, y_tolerance=2) if w["top"] < 70]
        hdr = [w for w in hdr if re.match(r"(Mon|Tue|Wed|Thu|Fri|Sat|Sun)", w["text"])]
        for d, w in zip(days, sorted(hdr, key=lambda w: w["x0"])): anchors[d] = w["x0"]

    grid = {d: [""] * len(mins_all) for d in anchors}
    colr = {d: [""] * len(mins_all) for d in anchors}
    for groups, tops, offset in ((EARLY, tops_early, 0), (LATE, tops_late, len(tops_early))):
        for pg, days in groups:
            page = pdf.pages[pg]
            words = page.extract_words(keep_blank_chars=True, x_tolerance=2, y_tolerance=2)
            for w in words:
                if w["x0"] < 150 and pg in (0, 4): continue
                i = min(range(len(tops)), key=lambda k: abs(tops[k] - w["top"]))
                if abs(tops[i] - w["top"]) > 5: continue
                d = min(days, key=lambda k: abs(anchors[k] - w["x0"]))
                if abs(anchors[d] - w["x0"]) > 40: continue
                grid[d][offset + i] = w["text"].strip()
                colr[d][offset + i] = color_at(page.rects, anchors[d] + 4, w["top"] + 6)

    weekly = {}
    for d in ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]:
        items = []
        for i, raw in enumerate(grid[d]):
            if not raw: continue
            title, ep = clean(raw)
            block = colr[d][i]
            hh, mm = divmod(mins_all[i], 60)
            hh %= 24
            if items and items[-1]["title"] == title and items[-1]["block"] == block and items[-1]["ep"] == ep:
                continue  # merge consecutive identical rows
            note = ""
            if title == "Boomerang": note = BOOMERANG_NOTE
            if title in ("Cartoon Theater", "Horror Movie"): note = THEATER_NOTE
            lookup = "-" if title in NO_LOOKUP else LOOKUP.get(title, "")
            items.append({"t": "%02d:%02d" % (hh, mm), "title": title, "block": block, "ep": ep, "lookup": lookup, "note": note})
        weekly[d] = items

    out = {"tz": "America/New_York", "weekly": weekly, "dated": DATED}
    with open(OUT, "w", encoding="utf-8") as f: json.dump(out, f, ensure_ascii=False, indent=1)
    print("wrote", OUT, {d: len(v) for d, v in weekly.items()})

if __name__ == "__main__":
    main()
