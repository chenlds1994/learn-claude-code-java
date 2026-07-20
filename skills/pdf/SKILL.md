---
name: pdf
description: Process PDF files - extract text, create PDFs, merge documents. Use when user asks to read PDF, create PDF, or work with PDF files.
---

# PDF Processing Skill

You now have expertise in PDF manipulation. Follow these workflows:

## Reading PDFs

**Option 1: Quick text extraction (preferred)**
```bash
pdftotext input.pdf -
pdftotext input.pdf output.txt
```

## Creating PDFs

**Option 1: From Markdown**
```bash
pandoc input.md -o output.pdf
```

**Option 2: Programmatically**
```python
from reportlab.pdfgen import canvas
c = canvas.Canvas("output.pdf")
c.drawString(100, 750, "Hello, PDF!")
c.save()
```

## Merging PDFs

```python
import fitz
result = fitz.open()
for pdf_path in ["file1.pdf", "file2.pdf"]:
    doc = fitz.open(pdf_path)
    result.insert_pdf(doc)
result.save("merged.pdf")
```

## Best Practices

1. **Check tool availability first**
2. **Handle encoding issues**
3. **Large PDFs page-by-page**
4. **OCR for scanned PDFs if needed**
