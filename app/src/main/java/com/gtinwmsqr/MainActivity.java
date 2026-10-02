package com.gtinwmsqr;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import org.json.JSONArray; import org.json.JSONObject;
import java.io.*; import java.util.*; import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static final int REQ=1001;
    TextRecognizer textRecognizer;
    boolean ocrDetected=false; boolean torchOn=false; androidx.camera.core.Camera activeCamera; PreviewView preview; TextView status, info, notFound; EditText input; Button scanBtn; ImageView qr; LinearLayout manualPanel; FrameLayout cameraCard; ScrollView resultScroll; ImageButton flashButton; Button manualButton; Button wmsModeButton; Button textQrModeButton; Button changeModeButton; LinearLayout modeSelection; ImageAnalysis analysis; boolean textQrMode=false; BarcodeScanner scanner; Map<String,Product> products=new HashMap<>(); Product last;
    LinearLayout ocrQrContainer;
    static class Product { String wms, gtin, partner, status; Product(JSONObject o){gtin=o.optString("pbarcode_canonical");wms=o.optString("wms_barcode");partner=o.optString("id_partner");status=o.optString("status");} }
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);bind();loadCatalog();scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS).build());
        textRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        showModeScreen();}

    void bind(){
        preview=findViewById(R.id.preview); status=findViewById(R.id.statusText); info=findViewById(R.id.productInfo);
        notFound=findViewById(R.id.notFoundText); input=findViewById(R.id.gtinInput); qr=findViewById(R.id.qrImage);
        manualPanel=findViewById(R.id.manualPanel); cameraCard=findViewById(R.id.cameraCard); resultScroll=findViewById(R.id.resultScroll);
        flashButton=findViewById(R.id.flashButton); manualButton=findViewById(R.id.manualButton);
        ocrQrContainer=findViewById(R.id.ocrQrContainer);
        modeSelection=findViewById(R.id.modeSelection);
        wmsModeButton=findViewById(R.id.wmsModeButton);
        textQrModeButton=findViewById(R.id.textQrModeButton);
        changeModeButton=findViewById(R.id.changeModeButton);

        changeModeButton.setOnClickListener(v->showModeScreen());

        wmsModeButton.setOnClickListener(v->{textQrMode=false;openScannerMode();});
        textQrModeButton.setOnClickListener(v->{textQrMode=true;openScannerMode();});

        findViewById(R.id.findButton).setOnClickListener(v->find(input.getText().toString()));
        findViewById(R.id.againButton).setOnClickListener(v->{resultScroll.setVisibility(View.GONE);cameraCard.setVisibility(View.VISIBLE);manualPanel.setVisibility(View.GONE);notFound.setVisibility(View.GONE);startCamera();});
        findViewById(R.id.saveButton).setOnClickListener(v->saveQr()); findViewById(R.id.printButton).setOnClickListener(v->printQr());
        manualButton.setOnClickListener(v->{manualPanel.setVisibility(View.VISIBLE);input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        flashButton.setOnClickListener(v->toggleTorch());
    }
    void showModeScreen(){
        stopCamera();
        modeSelection.setVisibility(View.VISIBLE);
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);
        status.setText("Select scanning mode");
    }

    void openScannerMode(){
        ocrDetected=false;
        modeSelection.setVisibility(View.GONE);
        cameraCard.setVisibility(View.VISIBLE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);

        // Reset OCR result UI when entering either scanner mode.
        qr.setVisibility(View.VISIBLE);
        ocrQrContainer.setVisibility(View.GONE);
        ocrQrContainer.removeAllViews();

        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQ);
        } else {
            startCamera();
        }
    }

    void loadCatalog(){try(InputStream is=getAssets().open("catalog.json")){String s=new String(readAll(is),java.nio.charset.StandardCharsets.UTF_8);JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++){Product p=new Product(a.getJSONObject(i));products.put(p.gtin.trim(),p);} }catch(Exception e){status.setText("Catalog load error: "+e.getMessage());}}
    byte[] readAll(InputStream i)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    void toggleTorch(){
        if(activeCamera==null){ Toast.makeText(this,"Camera is still starting",Toast.LENGTH_SHORT).show(); return; }
        try { torchOn=!torchOn; activeCamera.getCameraControl().enableTorch(torchOn); } catch(Exception e){ Toast.makeText(this,"Flashlight unavailable",Toast.LENGTH_SHORT).show(); }
    }
    void startCamera(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Camera permission is required.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> f =
            ProcessCameraProvider.getInstance(this);

        f.addListener(()->{
            try{
                ProcessCameraProvider cp=f.get();

                Preview p=new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());

                analysis=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                if(textQrMode){
                    startOcrAnalyzer(analysis);
                }else{
                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        image->{
                            InputImage ii=InputImage.fromMediaImage(
                                image.getImage(),
                                image.getImageInfo().getRotationDegrees()
                            );

                            scanner.process(ii)
                                .addOnSuccessListener(bs->{
                                    for(com.google.mlkit.vision.barcode.common.Barcode x:bs){
                                        String v=x.getRawValue();

                                        if(v!=null&&!v.isEmpty()){
                                            runOnUiThread(()->find(v));
                                            break;
                                        }
                                    }
                                })
                                .addOnCompleteListener(x->image.close());
                        }
                    );
                }

                cp.unbindAll();

                activeCamera=cp.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    p,
                    analysis
                );

                status.setText(
                    textQrMode
                        ? "OCR camera ready — point at the GTIN text."
                        : "Camera ready — scan the product GTIN."
                );

            }catch(Exception e){
                status.setText("Camera error: "+e.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }

    void startOcrAnalyzer(ImageAnalysis analysis){
        analysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            image -> {

                InputImage imageInput = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
                );

                textRecognizer.process(imageInput)
                    .addOnSuccessListener(result -> {

                        if(ocrDetected){
                            image.close();
                            return;
                        }

                        java.util.LinkedHashSet<String> gtins =
                            new java.util.LinkedHashSet<>();

                        java.util.LinkedHashSet<String> wmsCodes =
                            new java.util.LinkedHashSet<>();

                        java.util.LinkedHashSet<String> locations =
                            new java.util.LinkedHashSet<>();

                        for(Text.TextBlock block : result.getTextBlocks()){

                            for(Text.Line line : block.getLines()){

                                String lineText = line.getText();

                                if(lineText == null || lineText.trim().isEmpty()){
                                    continue;
                                }

                                classifyOcrLine(
                                    lineText,
                                    gtins,
                                    wmsCodes,
                                    locations
                                );
                            }
                        }

                        if(!gtins.isEmpty()
                                || !wmsCodes.isEmpty()
                                || !locations.isEmpty()){

                            ocrDetected = true;

                            final java.util.ArrayList<String> finalGtins =
                                new java.util.ArrayList<>(gtins);

                            final java.util.ArrayList<String> finalWms =
                                new java.util.ArrayList<>(wmsCodes);

                            final java.util.ArrayList<String> finalLocations =
                                new java.util.ArrayList<>(locations);

                            runOnUiThread(() ->
                                showOcrResults(
                                    finalGtins,
                                    finalWms,
                                    finalLocations
                                )
                            );
                        }

                        image.close();

                    })
                    .addOnFailureListener(e -> image.close());
            }
        );
    }


    void classifyOcrLine(
        String raw,
        java.util.Set<String> gtins,
        java.util.Set<String> wmsCodes,
        java.util.Set<String> locations){

        String text = raw.trim();

        if(text.isEmpty()){
            return;
        }

        /*
         * Remove common OCR labels so values such as:
         * GTIN: 9880000038750
         * WMS: 11649903984P
         * LOCATION: DS28-03-01-04A
         * can also be recognized.
         */
        text = text.replaceAll(
            "(?i)\\b(PBARCODE|P-BARCODE|GTIN|WMS|BARCODE|LOCATION|LOC)\\s*[:#-]?\\s*",
            " "
        ).trim();

        /*
         * A line may contain more than one OCR element.
         * Check the complete line first, then individual tokens.
         */
        java.util.ArrayList<String> candidates =
            new java.util.ArrayList<>();

        candidates.add(text);

        String[] parts = text.split("\\s+");

        for(String part : parts){
            if(part != null && !part.trim().isEmpty()){
                candidates.add(part.trim());
            }
        }

        for(String candidate : candidates){

            String value = candidate.trim();

            if(value.isEmpty()){
                continue;
            }

            /*
             * Remove OCR punctuation around a value.
             * Keep internal hyphens because Location uses them.
             */
            value = value.replaceAll(
                "^[^A-Za-z0-9]+|[^A-Za-z0-9]+$",
                ""
            );

            if(value.isEmpty()){
                continue;
            }

            String normalized = value
                .toUpperCase(java.util.Locale.US)
                .replaceAll("\\s+", "");

            /*
             * 1. WMS BARCODE
             *
             * Catalog values look like:
             * 11649903984P
             */
            /*
             * 1. WMS BARCODE
             *
             * Accept catalog WMS values AND unknown WMS numbers
             * ending with P.
             *
             * Example:
             * 11649903984P
             */
            if(isKnownWms(normalized)
                    || normalized.matches("\\d{8,14}P")){

                wmsCodes.add(normalized);
                continue;
            }

            /*
             * 2. GTIN / P-BARCODE
             *
             * Accept catalog GTIN values AND unknown numeric
             * GTIN / barcode values.
             *
             * Supports 8-14 digit numeric values.
             */
            if(isKnownGtin(normalized)
                    || normalized.matches("\\d{8,14}")){

                gtins.add(normalized);
                continue;
            }

            /*
             * 3. LOCATION
             *
             * Example:
             * DS28-03-01-04A
             *
             * This deliberately requires the structured
             * warehouse-location format rather than accepting
             * arbitrary OCR text.
             */
            if(isLocation(normalized)){
                locations.add(normalized);
            }
        }
    }


    boolean isKnownGtin(String value){

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
    }


    boolean isKnownWms(String value){

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
    }


    boolean isLocation(String value){

        if(value == null){
            return false;
        }

        /*
         * Warehouse location format:
         * DS28-03-01-04A
         *
         * Allows similar structured locations while rejecting
         * ordinary words and random OCR sentences.
         */
        return value.matches(
            "(?i)[A-Z]{1,4}\\d{1,4}(?:-[A-Z0-9]{1,6}){2,5}"
        );
    }


    void showOcrResults(
        java.util.ArrayList<String> gtins,
        java.util.ArrayList<String> wmsCodes,
        java.util.ArrayList<String> locations){

        stopCamera();

        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);
        notFound.setVisibility(View.GONE);

        /*
         * Feature 2 uses its own QR container.
         * Hide the original single-QR view used by Feature 1.
         */
        qr.setVisibility(View.GONE);
        ocrQrContainer.setVisibility(View.VISIBLE);
        ocrQrContainer.removeAllViews();

        resultTitle.setText("OCR RESULTS");

        StringBuilder detectedInfo = new StringBuilder();

        if(!gtins.isEmpty()){
            detectedInfo.append("Pbarcode / GTIN detected: ")
                .append(gtins.size())
                .append("\n");
        }

        if(!wmsCodes.isEmpty()){
            detectedInfo.append("WMS barcode detected: ")
                .append(wmsCodes.size())
                .append("\n");
        }

        if(!locations.isEmpty()){
            detectedInfo.append("Location detected: ")
                .append(locations.size())
                .append("\n");
        }

        info.setText(detectedInfo.toString().trim());

        /*
         * Required order:
         * 1. GTIN
         * 2. WMS
         * 3. LOCATION
         */
        for(String value : gtins){
            addOcrQrCard("GTIN / P-BARCODE", value);
        }

        for(String value : wmsCodes){
            addOcrQrCard("WMS BARCODE", value);
        }

        for(String value : locations){
            addOcrQrCard("LOCATION", value);
        }

        status.setText(
            "OCR complete — "
            + (gtins.size() + wmsCodes.size() + locations.size())
            + " QR"
            + ((gtins.size() + wmsCodes.size() + locations.size()) == 1 ? "" : "s")
            + " generated."
        );

        resultScroll.post(() -> resultScroll.requestFocus());
    }


    void addOcrQrCard(String type, String value){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(18,18,18,18);

        LinearLayout.LayoutParams cardParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        cardParams.topMargin = 14;
        card.setLayoutParams(cardParams);
        card.setBackgroundColor(Color.rgb(23,23,23));

        TextView title = new TextView(this);
        title.setText(type);
        title.setTextColor(Color.rgb(119,119,119));
        title.setTextSize(12);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);

        TextView content = new TextView(this);
        content.setText(value);
        content.setTextColor(Color.WHITE);
        content.setTextSize(16);
        content.setGravity(android.view.Gravity.CENTER);
        content.setPadding(0,10,0,10);

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.WHITE);
        image.setPadding(8,8,8,8);

        LinearLayout.LayoutParams qrParams =
            new LinearLayout.LayoutParams(280,280);

        image.setLayoutParams(qrParams);

        card.addView(title);
        card.addView(content);
        card.addView(image);

        try{

            image.setImageBitmap(makeQr(value,800));

        }catch(Exception e){

            content.setText(
                value + "\n\nQR error: " + e.getMessage()
            );
        }

        ocrQrContainer.addView(card);
    }


    void showSameQrResult(String text){
        /*
         * Kept as a compatibility method for the previous OCR
         * implementation. Feature 2 now uses showOcrResults().
         */
        if(text == null || text.trim().isEmpty()){
            return;
        }

        java.util.ArrayList<String> gtins =
            new java.util.ArrayList<>();

        java.util.ArrayList<String> wms =
            new java.util.ArrayList<>();

        java.util.ArrayList<String> locations =
            new java.util.ArrayList<>();

        classifyOcrLine(text, gtins, wms, locations);

        if(!gtins.isEmpty()
                || !wms.isEmpty()
                || !locations.isEmpty()){

            showOcrResults(gtins, wms, locations);
        }
    }


    void showOcrPreview(String text){
        String cleaned=text.trim();
        if(cleaned.isEmpty())return;

        status.setText("OCR detected: " + cleaned.replace("\\n"," | "));
    }

    void stopCamera(){try{if(analysis!=null)analysis.clearAnalyzer(); if(activeCamera!=null)activeCamera.getCameraControl().enableTorch(false);}catch(Exception ignored){}activeCamera=null;analysis=null;status.setText("Camera stopped.");}
    void find(String v){String key=(v==null?"":v).trim();input.setText(key);notFound.setVisibility(View.GONE);if(key.isEmpty())return;Product p=products.get(key);if(p==null){notFound.setText("Product not found\n\nNo matching pbarcode_canonical was found in this catalog.\n\nGTIN: "+key);notFound.setVisibility(View.VISIBLE);return;}last=p;stopCamera();cameraCard.setVisibility(View.GONE);manualPanel.setVisibility(View.GONE);resultScroll.setVisibility(View.VISIBLE);info.setText("GTIN / pbarcode_canonical:  "+p.gtin+"\nWMS barcode:  "+p.wms+"\nPartner ID:  "+p.partner+"\nStatus:  "+p.status);try{qr.setImageBitmap(makeQr(p.wms,800));}catch(Exception e){status.setText("QR error: "+e.getMessage());}resultScroll.post(()->resultScroll.requestFocus());}
    Bitmap makeQr(String text,int size)throws WriterException{BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}
    void saveQr(){if(last==null)return;Bitmap b;try{b=makeQr(last.wms,1000);}catch(Exception e){Toast.makeText(this,"QR generation failed: "+e.getMessage(),Toast.LENGTH_LONG).show();return;}String name="QR-"+last.wms+".png";ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/GTIN-WMS-QR");try{android.net.Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);try(OutputStream o=getContentResolver().openOutputStream(u)){b.compress(Bitmap.CompressFormat.PNG,100,o);}Toast.makeText(this,"QR saved to Pictures/GTIN-WMS-QR",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void printQr(){if(last==null)return;PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("GTIN-WMS-QR-"+last.wms,new QrPrintAdapter(this,qr.getDrawable()),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());}
    @Override public void onBackPressed(){
        if(modeSelection!=null && modeSelection.getVisibility()==View.VISIBLE){
            super.onBackPressed();
            return;
        }

        showModeScreen();
    }

    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText("Camera permission denied. You can still enter a GTIN manually.");}
}
