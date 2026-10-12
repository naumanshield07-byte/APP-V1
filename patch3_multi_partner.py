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

def replace_method(name, new_body, label):
    """Find 'void NAME(' or 'Type NAME(' and brace-match to end."""
    global src
    start = src.find(name)
    if start == -1:
        print("ERROR: method not found:", name); sys.exit(1)
    # Walk back to start of line
    ls = src.rfind("\n", 0, start) + 1
    # Brace match
    depth = 0
    i = start
    while i < len(src):
        if src[i] == '{': depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                i += 1
                break
        i += 1
    src = src[:ls] + new_body + src[i:]
    changes.append(label)

# 1) Add fields: map -> List, plus current list, date, container
if not rep("    Map<String,SlKlProduct> slKlProducts = new HashMap<>();",
    """    Map<String,List<SlKlProduct>> slKlProducts = new HashMap<>();
    List<SlKlProduct> currentSlKlList = null;
    java.time.LocalDate slKlPickedDate = null;
    LinearLayout slKlCardsContainer = null;""",
    "field: map -> List + helpers"):
    print("ERROR: slKlProducts field"); sys.exit(1)

# 2) loadSlKlCatalog — accumulate into lists
replace_method("void loadSlKlCatalog(",
r'''    void loadSlKlCatalog(){
        slKlProducts.clear();

        try(InputStream is = getAssets().open("sl_kl.csv");
            BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    is,
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )){

            String line = br.readLine();
            if(line == null){
                throw new Exception("SL/KL file is empty");
            }

            int count = 0;

            while((line = br.readLine()) != null){
                if(line.trim().isEmpty()) continue;

                String[] parts = line.split(",", -1);
                if(parts.length < 4) continue;

                String gtin = parts[0].trim();
                String partner = parts[1].trim();
                String shelfLife = parts[2].trim();
                String keepLife = parts[3].trim();

                if(gtin.isEmpty()) continue;

                List<SlKlProduct> bucket = slKlProducts.get(gtin);
                if(bucket == null){
                    bucket = new java.util.ArrayList<>();
                    slKlProducts.put(gtin, bucket);
                }
                bucket.add(new SlKlProduct(gtin, partner, shelfLife, keepLife));
                count++;
            }

            status.setText("SL/KL catalog loaded: " + count + " products");

        }catch(Exception e){
            status.setText("SL/KL catalog load error: " + e.getMessage());
        }
    }''',
"loadSlKlCatalog accumulate")

# 3) Rewrite findSlKl
replace_method("void findSlKl(String v){",
r'''    void findSlKl(String v){
        String key=(v==null?"":v).trim();
        input.setText(key);
        notFound.setVisibility(View.GONE);

        if(key.isEmpty()) return;

        List<SlKlProduct> list = slKlProducts.get(key);

        if(list == null || list.isEmpty()){
            stopCamera();
            cameraCard.setVisibility(View.GONE);
            manualPanel.setVisibility(View.GONE);
            resultScroll.setVisibility(View.VISIBLE);

            resultTitle.setText("PRODUCT NOT FOUND");
            info.setVisibility(View.GONE);
            slKlResultCard.setVisibility(View.GONE);
            qrCard.setVisibility(View.GONE);
            qr.setVisibility(View.GONE);
            ocrQrContainer.setVisibility(View.GONE);

            notFound.setText(
                "No matching GTIN was found in the SL/KL catalog.\n\n" +
                "GTIN: "+key
            );
            notFound.setVisibility(View.VISIBLE);
            resultScroll.post(()->resultScroll.requestFocus());
            return;
        }

        saveHistoryEntry("GTIN", key, "sl_kl");

        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        resultTitle.setText("PRODUCT FOUND");
        info.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.GONE);
        qr.setVisibility(View.GONE);
        ocrQrContainer.setVisibility(View.VISIBLE);
        ocrQrContainer.removeAllViews();

        currentSlKlList = list;
        slKlPickedDate = null;

        /* GTIN header */
        TextView gtinHeader = new TextView(this);
        gtinHeader.setText("GTIN :  " + key);
        gtinHeader.setTextColor(Color.parseColor("#A855F7"));
        gtinHeader.setTextSize(15);
        gtinHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        gtinHeader.setGravity(android.view.Gravity.CENTER);
        gtinHeader.setPadding(0, 0, 0, 16);
        ocrQrContainer.addView(gtinHeader);

        /* Shared production-date button */
        Button dateBtn = new Button(this);
        dateBtn.setText("SELECT PRODUCTION DATE");
        dateBtn.setAllCaps(false);
        dateBtn.setTextColor(Color.BLACK);
        dateBtn.setTextSize(15);
        dateBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        dateBtn.setBackgroundResource(R.drawable.glow_button);
        dateBtn.setMinHeight((int)(56 * getResources()
                                    .getDisplayMetrics().density));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (int)(56 * getResources().getDisplayMetrics().density));
        bp.setMargins(0, 0, 0, 20);
        dateBtn.setOnClickListener(x -> showProductionDatePicker());
        ocrQrContainer.addView(dateBtn, bp);

        /* Cards container */
        slKlCardsContainer = new LinearLayout(this);
        slKlCardsContainer.setOrientation(LinearLayout.VERTICAL);
        ocrQrContainer.addView(slKlCardsContainer);

        renderSlKlCards();

        findViewById(R.id.saveButton).setVisibility(View.GONE);

        resultScroll.post(()->resultScroll.requestFocus());
    }''',
"findSlKl rewritten")

# 4) Rewrite showProductionDatePicker (no args, uses currentSlKlList)
replace_method("void showProductionDatePicker(",
r'''    void showProductionDatePicker(){
        if(currentSlKlList == null || currentSlKlList.isEmpty()) return;

        java.util.Calendar today = java.util.Calendar.getInstance();

        android.app.DatePickerDialog dialog =
            new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    try{
                        slKlPickedDate = java.time.LocalDate.of(
                            year, month + 1, dayOfMonth);
                        renderSlKlCards();
                    }catch(Exception e){
                        status.setText("Invalid date");
                    }
                },
                today.get(java.util.Calendar.YEAR),
                today.get(java.util.Calendar.MONTH),
                today.get(java.util.Calendar.DAY_OF_MONTH)
            );

        dialog.setTitle("SELECT PRODUCTION DATE");
        dialog.show();
    }''',
"showProductionDatePicker rewritten")

# 5) Insert renderSlKlCards + buildSlKlCard before addWmsResultCard
if "void renderSlKlCards(" not in src:
    anchor = "    void addWmsResultCard(final Product p){"
    if anchor not in src:
        print("ERROR: addWmsResultCard anchor not found"); sys.exit(1)

    method = r'''    void renderSlKlCards(){
        if(slKlCardsContainer == null) return;
        slKlCardsContainer.removeAllViews();
        if(currentSlKlList == null) return;

        for(SlKlProduct p : currentSlKlList){
            slKlCardsContainer.addView(buildSlKlCard(p));
        }
    }

    View buildSlKlCard(SlKlProduct p){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        card.setBackgroundResource(R.drawable.glow_card);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cp);

        /* Compute dates */
        String shelfDateStr = "—";
        String keepDateStr  = "—";
        String remainStr    = "—";
        String statusStr    = "—";
        int    statusColor  = Color.parseColor("#A855F7");

        java.time.format.DateTimeFormatter fmt =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        if(slKlPickedDate != null){
            try{
                if(p.shelfLife != null && !p.shelfLife.isEmpty()){
                    long days = Long.parseLong(p.shelfLife.trim());
                    shelfDateStr = slKlPickedDate.plusDays(days).format(fmt);
                }
            }catch(Exception ignored){}

            try{
                if(p.keepLife != null && !p.keepLife.isEmpty()){
                    long days = Long.parseLong(p.keepLife.trim());
                    java.time.LocalDate keepDate =
                        slKlPickedDate.plusDays(days);
                    keepDateStr = keepDate.format(fmt);

                    long remaining = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), keepDate);

                    if(remaining <= 0){
                        remainStr = "0 days";
                        statusStr = "EXPIRED";
                        statusColor = Color.parseColor("#FF3333");
                    }else{
                        remainStr = remaining + " days";
                        if(remaining <= 3){
                            statusStr = "NEAR EXPIRY";
                            statusColor = Color.parseColor("#FFA500");
                        }else{
                            statusStr = "GOOD";
                            statusColor = Color.parseColor("#22CC44");
                        }
                    }
                }
            }catch(Exception ignored){}
        }

        /* Row helper */
        java.util.function.BiConsumer<String,String> addRow =
            (label, value) -> {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, 10, 0, 10);

                TextView l = new TextView(this);
                l.setText(label);
                l.setTextColor(Color.parseColor("#A855F7"));
                l.setTextSize(14);
                l.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(l,
                    new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView v = new TextView(this);
                v.setText(value);
                v.setTextColor(Color.WHITE);
                v.setTextSize(15);
                v.setTypeface(null, android.graphics.Typeface.BOLD);
                v.setGravity(android.view.Gravity.END);
                row.addView(v,
                    new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

                card.addView(row);
            };

        addRow.accept("PARTNER ID",
            (p.partner == null || p.partner.isEmpty()) ? "—" : p.partner);
        addRow.accept("SHELF LIFE",
            (p.shelfLife == null || p.shelfLife.isEmpty())
                ? "—" : p.shelfLife + " days");
        addRow.accept("KEEP LIFE",
            (p.keepLife == null || p.keepLife.isEmpty())
                ? "—" : p.keepLife + " days");
        addRow.accept("PRODUCTION DATE",
            slKlPickedDate == null ? "—" : slKlPickedDate.format(fmt));
        addRow.accept("SHELF LIFE DATE", shelfDateStr);
        addRow.accept("KEEP LIFE DATE",  keepDateStr);
        addRow.accept("REMAINING",       remainStr);

        /* STATUS colored */
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, 12, 0, 4);

        TextView sl = new TextView(this);
        sl.setText("STATUS");
        sl.setTextColor(Color.parseColor("#A855F7"));
        sl.setTextSize(14);
        sl.setTypeface(null, android.graphics.Typeface.BOLD);
        statusRow.addView(sl,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView sv = new TextView(this);
        sv.setText(statusStr);
        sv.setTextColor(statusColor);
        sv.setTextSize(18);
        sv.setTypeface(null, android.graphics.Typeface.BOLD);
        sv.setLetterSpacing(0.08f);
        sv.setGravity(android.view.Gravity.END);
        statusRow.addView(sv,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

        card.addView(statusRow);

        return card;
    }

'''
    src = src.replace(anchor, method + anchor, 1)
    changes.append("renderSlKlCards + buildSlKlCard inserted")

JAVA.write_text(src)

print("=== Changes applied ===")
for c in changes:
    print(" OK:", c)
print()
print("Braces: { %d  } %d" % (src.count('{'), src.count('}')))
print("DONE")
