import sys
from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

path = sys.argv[1]
out = sys.argv[2]
doc = Document(path)
lines = []

for child in doc.element.body.iterchildren():
    if child.tag.endswith('}p'):
        p = Paragraph(child, doc)
        text = p.text.strip()
        if text:
            lines.append(f"[{p.style.name}] {text}")
    elif child.tag.endswith('}tbl'):
        t = Table(child, doc)
        lines.append("[TABLE]")
        for row in t.rows:
            cells = []
            for cell in row.cells:
                cells.append(" / ".join(p.text.strip() for p in cell.paragraphs if p.text.strip()))
            lines.append(" | ".join(cells))
        lines.append("[/TABLE]")

with open(out, 'w', encoding='utf-8') as f:
    f.write("\n".join(lines))
print(f"Wrote {len(lines)} lines to {out}")
print(f"Paragraphs: {len(doc.paragraphs)}, Tables: {len(doc.tables)}")
