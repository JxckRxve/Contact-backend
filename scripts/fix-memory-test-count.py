from pathlib import Path
p=Path('tests/gos-core-smoke.js')
s=p.read_text()
old='assert(state.body.counts.memories === 1, "memory persistence failed");'
new='assert(state.body.counts.memories >= 5, "memory persistence failed");'
if old not in s:
    raise SystemExit('memory count assertion not found')
p.write_text(s.replace(old,new,1))
