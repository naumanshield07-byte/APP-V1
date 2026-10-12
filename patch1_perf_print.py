#!/usr/bin/env python3
import pathlib, re, sys

ROOT = pathlib.Path.home() / "APP-V1"
JAVA = ROOT / "app/src/main/java/com/gtinwmsqr/MainActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"

src = JAVA.read_text()
changes = []

# ============================================================
# 1) Remove PrintManager import
# ============================================================
if "import android.print.PrintManager;" in src:
    src = src.replace("import android.print.PrintManager;\n", "")
    changes.append("removed PrintManager import")

# ============================================================
# 2) Remove printButton setOnClickListener
# ============================================================
old_hook = 'findViewById(R.id.saveButton).setOnClickListener(v->saveQr()); findViewById(R.id.printButton).setOnClickListener(v->printQr());'
new_hook = 'findViewById(R.id.saveButton).setOnClickListener(v->saveQr());'
if old_hook in src:
    src = src.replace(old_hook, new_hook, 1)
    changes.append("removed printButton setOnClickListener")

# ============================================================
# 3) Remove printButton setVisibility calls (2 occurrences)
# ============================================================
for pat in ['findViewById(R.id.printButton).setVisibility(View.GONE);',
            'findViewById(R.id.printButton).setVisibility(View.VISIBLE);']:
    count = src.count(pat)
    if count:
        src = src.replace(pat, "")
        changes.append(f"removed printButton setVisibility ({count}x)")

# ============================================================
# 4) Remove printQr method
# ============================================================
# Match "    void printQr(){...}" on one line (it's a one-liner)
m = re.search(r'\n[ \t]*void printQr\(\)\{[^\n]*\}\n', src)
if m:
    src = src[:m.start()] + "\n" + src[m.end():]
    changes.append("removed printQr() method")

# ============================================================
# 5) Add qrExecutor field (inline after bypassGtinChecksum)
# ============================================================
if "ExecutorService qrExecutor" not in src:
    m = re.search(r'boolean\s+bypassGtinChecksum\s*=\s*false\s*;', src)
    if m:
        src = (src[:m.end()]
               + " java.util.concurrent.ExecutorService qrExecutor = java.util.concurrent.Executors.newFixedThreadPool(2);"
               + src[m.end():])
        changes.append("added qrExecutor field")

# ============================================================
# 6) Add onDestroy to shutdown executor
# ============================================================
if "protected void onDestroy()" not in src:
    # Insert before the final closing brace of the class
    last_brace = src.rfind("}")
    ondestroy = '''
    @Override
    protected void onDestroy(){
        super.onDestroy();
        try{ if(qrExecutor != null) qrExecutor.shutdownNow(); }catch(Exception ignored){}
    }
'''
    src = src[:last_brace] + ondestroy + src[last_brace:]
    changes.append("added onDestroy() to shutdown executor")

# ============================================================
# 7) Make addOcrQrCard QR generation async + smaller size
# ============================================================
# Match the try-catch inside addOcrQrCard
old_qr = '''        try{
            image.setImageBitmap(makeQr(value,800));
        }catch(Exception e){
            content.setText(
                value + "\\n\\nQR error: " + e.getMessage()
            );
        }'''

new_qr = '''        final String qrVal = value;
        final ImageView qrImg = image;
        final TextView qrContent = content;
        qrExecutor.submit(() -> {
            try{
                final Bitmap bmp = makeQr(qrVal, 600);
                runOnUiThread(() -> qrImg.setImageBitmap(bmp));
            }catch(final Exception ex){
                runOnUiThread(() -> qrContent.setText(
                    qrVal + "\\n\\nQR error: " + ex.getMessage()));
            }
        });'''

if old_qr in src:
    src = src.replace(old_qr, new_qr, 1)
    changes.append("addOcrQrCard -> async QR (600px)")
else:
    # Try the 1200px variant
    old_qr2 = '''        try{
            image.setImageBitmap(makeQr(value,1200));
        }catch(Exception e){
            content.setText(
                value + "\\n\\nQR error: " + e.getMessage()
            );
        }'''
    new_qr2 = new_qr.replace('makeQr(qrVal, 600)', 'makeQr(qrVal, 600)')
    if old_qr2 in src:
        src = src.replace(old_qr2, new_qr2, 1)
        changes.append("addOcrQrCard -> async QR (600px, was 1200)")

JAVA.write_text(src)

# ============================================================
# 8) Remove printButton from layout XML
# ============================================================
lsrc = LAYOUT.read_text()
# Match a Button block containing @+id/printButton
m = re.search(
    r'<Button[^>]*?@\+id/printButton.*?/>',
    lsrc, re.DOTALL
)
if m:
    lsrc = lsrc[:m.start()] + lsrc[m.end():]
    changes.append("removed printButton from layout")

LAYOUT.write_text(lsrc)

# ============================================================
# Report
# ============================================================
print("=== Changes applied ===")
for c in changes:
    print(" OK:", c)
if not changes:
    print(" (nothing changed — check patterns)")

ob = src.count('{'); cb = src.count('}')
print()
print("Braces: { %d  } %d" % (ob, cb))
print("DONE")
