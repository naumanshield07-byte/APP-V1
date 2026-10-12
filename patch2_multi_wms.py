#!/usr/bin/env python3
import pathlib, sys

JAVA = pathlib.Path.home() / "APP-V1/app/src/main/java/com/gtinwmsqr/MainActivity.java"
src = JAVA.read_text()
changes = []

def rep(old, new, label, required=True):
    global src
    if old in src:
        src = src.replace(old, new, 1)
        changes.append(label)
    elif required:
        print("ERROR: could not find:", label)
        print("---- expected ----")
        print(old[:400])
        sys.exit(1)

# ============================================================
# 1) Field type: Map<String,Product> -> Map<String,List<Product>>
# ============================================================
rep(
"Map<String,Product> products=new HashMap<>();",
"Map<String,List<Product>> products=new HashMap<>();",
"field type -> Map<String,List<Product>>")

# ============================================================
# 2) parseCatalog signature
# ============================================================
rep(
"    Map<String,Product> parseCatalog(String json) throws Exception{",
"    Map<String,List<Product>> parseCatalog(String json) throws Exception{",
"parseCatalog return type")

rep(
"        Map<String,Product> parsed = new HashMap<>();",
"        Map<String,List<Product>> parsed = new HashMap<>();",
"parseCatalog local map")

# Accumulate into lists instead of overwrite
rep(
"            p.gtin = gtin;\n            p.wms = wms;\n\n            parsed.put(gtin,p);",
"""            p.gtin = gtin;
            p.wms = wms;

            List<Product> bucket = parsed.get(gtin);
            if(bucket == null){
                bucket = new java.util.ArrayList<>();
                parsed.put(gtin, bucket);
            }
            bucket.add(p);""",
"parseCatalog accumulate into lists")

# ============================================================
# 3) loadCatalog local types
# ============================================================
rep(
"                Map<String,Product> importedProducts =\n                    parseCatalog(s);",
"                Map<String,List<Product>> importedProducts =\n                    parseCatalog(s);",
"loadCatalog importedProducts type")

rep(
"                Map<String,Product> bundledProducts =\n                    parseCatalog(s);",
"                Map<String,List<Product>> bundledProducts =\n                    parseCatalog(s);",
"loadCatalog bundledProducts type")

# ============================================================
# 4) onActivityResult local type
# ============================================================
rep(
"            Map<String,Product> newProducts =\n                parseCatalog(json);",
"            Map<String,List<Product>> newProducts =\n                parseCatalog(json);",
"onActivityResult newProducts type")

# ============================================================
# 5) isKnownGtin
# ============================================================
old_gtin = """    boolean isKnownGtin(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        if(products.containsKey(value)){
            return true;
        }

        for(Product product : products.values()){

            if(product.gtin != null
                    && product.gtin.trim().equalsIgnoreCase(value)){

                return true;
            }
        }

        return false;
    }"""

new_gtin = """    boolean isKnownGtin(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        if(products.containsKey(value)){
            return true;
        }

        for(List<Product> bucket : products.values()){
            for(Product product : bucket){
                if(product.gtin != null
                        && product.gtin.trim().equalsIgnoreCase(value)){
                    return true;
                }
            }
        }

        return false;
    }"""
rep(old_gtin, new_gtin, "isKnownGtin iterate lists")

# ============================================================
# 6) isKnownWms
# ============================================================
old_wms = """    boolean isKnownWms(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        for(Product product : products.values()){

            if(product.wms != null
                    && product.wms.trim().equalsIgnoreCase(value)){

                return true;
            }
        }

        return false;
    }"""

new_wms = """    boolean isKnownWms(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        for(List<Product> bucket : products.values()){
            for(Product product : bucket){
                if(product.wms != null
                        && product.wms.trim().equalsIgnoreCase(value)){
                    return true;
                }
            }
        }

        return false;
    }"""
rep(old_wms, new_wms, "isKnownWms iterate lists")

# ============================================================
# 7) find() -- replace single-product path with multi-card render
# ============================================================
old_find = """        Product p=products.get(key);

        if(p==null){
            notFound.setText(
                "Product not found\\n\\n" +
                "No matching pbarcode_canonical was found in this catalog.\\n\\n" +
                "GTIN: "+key
            );
            notFound.setVisibility(View.VISIBLE);
            return;
        }

        last=p;
        saveHistoryEntry("GTIN", p.gtin, "lookup");
        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        resultTitle.setText("PRODUCT FOUND");
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.VISIBLE);
        findViewById(R.id.saveButton).setVisibility(View.VISIBLE);


        info.setText("");
        info.setVisibility(View.GONE);

        addFeature1Actions(p);

        try{
            qr.setVisibility(View.VISIBLE);
            ocrQrContainer.setVisibility(View.GONE);
            qr.setImageBitmap(makeQr(p.wms,800));
        }catch(Exception e){
            status.setText("QR error: "+e.getMessage());
        }

        resultScroll.post(()->resultScroll.requestFocus());
    }"""

new_find = """        List<Product> list = products.get(key);

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

        /* GTIN header above all WMS cards */
        TextView gtinHeader = new TextView(this);
        gtinHeader.setText("GTIN :  " + list.get(0).gtin);
        gtinHeader.setTextColor(Color.parseColor("#A855F7"));
        gtinHeader.setTextSize(15);
        gtinHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        gtinHeader.setGravity(android.view.Gravity.CENTER);
        gtinHeader.setPadding(0, 0, 0, 16);
        ocrQrContainer.addView(gtinHeader);

        /* One card per WMS barcode */
        for(Product prod : list){
            addWmsResultCard(prod);
        }

        resultScroll.post(()->resultScroll.requestFocus());
    }"""
rep(old_find, new_find, "find() -> multi-WMS cards")

# ============================================================
# 8) Insert addWmsResultCard() helper before addOcrQrCard
# ============================================================
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
            + (p.partner == null || p.partner.isEmpty() ? "—" : p.partner));
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
