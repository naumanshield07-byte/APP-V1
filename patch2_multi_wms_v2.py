#!/usr/bin/env python3
import pathlib, sys

JAVA = pathlib.Path.home() / "APP-V1/app/src/main/java/com/gtinwmsqr/MainActivity.java"
src = JAVA.read_text()
changes = []

def rep(old, new, label):
    global src
    if old in src:
        src = src.replace(old, new, 1)
        changes.append(label)
        return True
    return False

# 1) Field type
if rep("Map<String,Product> products=new HashMap<>();",
       "Map<String,List<Product>> products=new HashMap<>();",
       "field type"):
    pass
else:
    print("ERROR: field type"); sys.exit(1)

# 2) parseCatalog signature
if not rep("    Map<String,Product> parseCatalog(String json) throws Exception{",
           "    Map<String,List<Product>> parseCatalog(String json) throws Exception{",
           "parseCatalog signature"):
    print("ERROR: parseCatalog signature"); sys.exit(1)

# 3) parseCatalog local map
if not rep("        Map<String,Product> parsed = new HashMap<>();",
           "        Map<String,List<Product>> parsed = new HashMap<>();",
           "parseCatalog local map"):
    print("ERROR: parseCatalog local"); sys.exit(1)

# 4) Accumulate (loose match)
old_accum = "            parsed.put(gtin,p);"
new_accum = """            List<Product> bucket = parsed.get(gtin);
            if(bucket == null){
                bucket = new java.util.ArrayList<>();
                parsed.put(gtin, bucket);
            }
            bucket.add(p);"""
if not rep(old_accum, new_accum, "parseCatalog accumulate"):
    print("ERROR: parseCatalog accumulate"); sys.exit(1)

# 5) loadCatalog locals
if not rep("Map<String,Product> importedProducts =",
           "Map<String,List<Product>> importedProducts =",
           "importedProducts type"):
    print("WARN: importedProducts type (may be OK)")
if not rep("Map<String,Product> bundledProducts =",
           "Map<String,List<Product>> bundledProducts =",
           "bundledProducts type"):
    print("WARN: bundledProducts type")

# 6) onActivityResult
if not rep("Map<String,Product> newProducts =",
           "Map<String,List<Product>> newProducts =",
           "newProducts type"):
    print("WARN: newProducts type")

# 7) isKnownGtin loop
old_gtin_loop = """        for(Product product : products.values()){

            if(product.gtin != null
                    && product.gtin.trim().equalsIgnoreCase(value)){

                return true;
            }
        }"""
new_gtin_loop = """        for(List<Product> bucket : products.values()){
            for(Product product : bucket){
                if(product.gtin != null
                        && product.gtin.trim().equalsIgnoreCase(value)){
                    return true;
                }
            }
        }"""
if not rep(old_gtin_loop, new_gtin_loop, "isKnownGtin loop"):
    print("WARN: isKnownGtin loop")

# 8) isKnownWms loop
old_wms_loop = """        for(Product product : products.values()){

            if(product.wms != null
                    && product.wms.trim().equalsIgnoreCase(value)){

                return true;
            }
        }"""
new_wms_loop = """        for(List<Product> bucket : products.values()){
            for(Product product : bucket){
                if(product.wms != null
                        && product.wms.trim().equalsIgnoreCase(value)){
                    return true;
                }
            }
        }"""
if not rep(old_wms_loop, new_wms_loop, "isKnownWms loop"):
    print("WARN: isKnownWms loop")

# 9) FIND() — brace matching replacement
find_start = src.find("    void find(String v){")
if find_start == -1:
    print("ERROR: find() not found"); sys.exit(1)

depth = 0
i = find_start
while i < len(src):
    if src[i] == '{': depth += 1
    elif src[i] == '}':
        depth -= 1
        if depth == 0:
            i += 1
            break
    i += 1

new_find = '''    void find(String v){
        String key=(v==null?"":v).trim();
        input.setText(key);
        notFound.setVisibility(View.GONE);

        if(key.isEmpty())return;

        List<Product> list = products.get(key);

        if(list == null || list.isEmpty()){
            notFound.setText(
                "Product not found\\n\\n" +
                "No matching pbarcode_canonical was found in this catalog.\\n\\n" +
                "GTIN: "+key
            );
            notFound.setVisibility(View.VISIBLE);
            return;
        }

        last = list.get(0);
        saveHistoryEntry("GTIN", list.get(0).gtin, "lookup");
        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        resultTitle.setText("PRODUCT FOUND");
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.GONE);
        qr.setVisibility(View.GONE);
        ocrQrContainer.setVisibility(View.VISIBLE);
        ocrQrContainer.removeAllViews();

        findViewById(R.id.saveButton).setVisibility(View.VISIBLE);

        info.setText("");
        info.setVisibility(View.GONE);

        TextView gtinHeader = new TextView(this);
        gtinHeader.setText("GTIN :  " + list.get(0).gtin);
        gtinHeader.setTextColor(Color.parseColor("#A855F7"));
        gtinHeader.setTextSize(15);
        gtinHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        gtinHeader.setGravity(android.view.Gravity.CENTER);
        gtinHeader.setPadding(0, 0, 0, 16);
        ocrQrContainer.addView(gtinHeader);

        for(Product prod : list){
            addWmsResultCard(prod);
        }

        resultScroll.post(()->resultScroll.requestFocus());
    }'''

src = src[:find_start] + new_find + src[i:]
changes.append("find() rewritten")

# 10) addWmsResultCard
if "void addWmsResultCard(" not in src:
    anchor = "    void addOcrQrCard(String type, String value){"
    if anchor not in src:
        print("ERROR: addOcrQrCard anchor not found"); sys.exit(1)

    method = '''    void addWmsResultCard(final Product p){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18,18,18,18);
        card.setBackgroundResource(R.drawable.glow_card);

        TextView title = new TextView(this);
        title.setText("WMS BARCODE");
        title.setTextColor(Color.parseColor("#A855F7"));
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        card.addView(title);

        TextView qrBadge = new TextView(this);
        qrBadge.setText("QR GENERATED");
        qrBadge.setTextColor(Color.BLACK);
        qrBadge.setTextSize(15);
        qrBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        qrBadge.setGravity(android.view.Gravity.CENTER);
        qrBadge.setPadding(16,10,16,10);
        qrBadge.setBackgroundResource(R.drawable.glow_button);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.gravity = android.view.Gravity.CENTER;
        bp.setMargins(0,10,0,10);
        card.addView(qrBadge, bp);

        final TextView wmsValue = new TextView(this);
        wmsValue.setText(p.wms);
        wmsValue.setTextColor(Color.WHITE);
        wmsValue.setTextSize(16);
        wmsValue.setGravity(android.view.Gravity.CENTER);
        wmsValue.setPadding(0,10,0,6);
        card.addView(wmsValue);

        TextView partner = new TextView(this);
        partner.setText("Partner ID :  "
            + (p.partner == null || p.partner.isEmpty() ? "\\u2014" : p.partner));
        partner.setTextColor(Color.parseColor("#A855F7"));
        partner.setTextSize(14);
        partner.setTypeface(null, android.graphics.Typeface.BOLD);
        partner.setGravity(android.view.Gravity.CENTER);
        partner.setPadding(0,0,0,10);
        card.addView(partner);

        final ImageView image = new ImageView(this);
        int qrSide = (int)(280 * getResources()
                              .getDisplayMetrics().density);
        LinearLayout.LayoutParams imgParams =
            new LinearLayout.LayoutParams(qrSide, qrSide);
        imgParams.setMargins(20, 16, 20, 16);
        imgParams.gravity = android.view.Gravity.CENTER;
        image.setLayoutParams(imgParams);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.WHITE);
        card.addView(image);

        final String wmsVal = p.wms;
        qrExecutor.submit(() -> {
            try{
                final Bitmap bmp = makeQr(wmsVal, 600);
                runOnUiThread(() -> image.setImageBitmap(bmp));
            }catch(final Exception ex){
                runOnUiThread(() -> status.setText(
                    "QR error: " + ex.getMessage()));
            }
        });

        LinearLayout actions = createActionRow(
            () -> editValueDialog(
                "Edit WMS",
                p.wms,
                newValue -> {
                    if(newValue == null || newValue.isEmpty()) return;
                    p.wms = newValue;
                    wmsValue.setText(newValue);
                    final String nv = newValue;
                    qrExecutor.submit(() -> {
                        try{
                            final Bitmap bmp = makeQr(nv, 600);
                            runOnUiThread(() -> image.setImageBitmap(bmp));
                        }catch(Exception ignored){}
                    });
                }
            ),
            () -> copyValue(p.wms),
            () -> shareValue("WMS", p.wms)
        );
        card.addView(actions);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0,0,0,24);

        ocrQrContainer.addView(card, cardParams);
    }

'''
    src = src.replace(anchor, method + anchor, 1)
    changes.append("addWmsResultCard() inserted")

JAVA.write_text(src)

print("=== Changes applied ===")
for c in changes:
    print(" OK:", c)
print()
print("Braces: { %d  } %d" % (src.count('{'), src.count('}')))
print("DONE")
