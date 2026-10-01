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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import org.json.JSONArray; import org.json.JSONObject;
import java.io.*; import java.util.*; import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static final int REQ=1001; boolean torchOn=false; androidx.camera.core.Camera activeCamera; PreviewView preview; TextView status, info, notFound; EditText input; Button scanBtn; ImageView qr; LinearLayout manualPanel; FrameLayout cameraCard; ScrollView resultScroll; ImageButton flashButton; Button manualButton; ImageAnalysis analysis; BarcodeScanner scanner; Map<String,Product> products=new HashMap<>(); Product last;
    static class Product { String wms, gtin, partner, status; Product(JSONObject o){gtin=o.optString("pbarcode_canonical");wms=o.optString("wms_barcode");partner=o.optString("id_partner");status=o.optString("status");} }
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);bind();loadCatalog();scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS).build()); if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQ); else startCamera();}
    void bind(){
        preview=findViewById(R.id.preview); status=findViewById(R.id.statusText); info=findViewById(R.id.productInfo);
        notFound=findViewById(R.id.notFoundText); input=findViewById(R.id.gtinInput); qr=findViewById(R.id.qrImage);
        manualPanel=findViewById(R.id.manualPanel); cameraCard=findViewById(R.id.cameraCard); resultScroll=findViewById(R.id.resultScroll);
        flashButton=findViewById(R.id.flashButton); manualButton=findViewById(R.id.manualButton);
        findViewById(R.id.findButton).setOnClickListener(v->find(input.getText().toString()));
        findViewById(R.id.againButton).setOnClickListener(v->{resultScroll.setVisibility(View.GONE);cameraCard.setVisibility(View.VISIBLE);manualPanel.setVisibility(View.GONE);notFound.setVisibility(View.GONE);startCamera();});
        findViewById(R.id.saveButton).setOnClickListener(v->saveQr()); findViewById(R.id.printButton).setOnClickListener(v->printQr());
        manualButton.setOnClickListener(v->{manualPanel.setVisibility(View.VISIBLE);input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        flashButton.setOnClickListener(v->toggleTorch());
    }
    void loadCatalog(){try(InputStream is=getAssets().open("catalog.json")){String s=new String(readAll(is),java.nio.charset.StandardCharsets.UTF_8);JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++){Product p=new Product(a.getJSONObject(i));products.put(p.gtin.trim(),p);} }catch(Exception e){status.setText("Catalog load error: "+e.getMessage());}}
    byte[] readAll(InputStream i)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    void toggleTorch(){
        if(activeCamera==null){ Toast.makeText(this,"Camera is still starting",Toast.LENGTH_SHORT).show(); return; }
        try { torchOn=!torchOn; activeCamera.getCameraControl().enableTorch(torchOn); } catch(Exception e){ Toast.makeText(this,"Flashlight unavailable",Toast.LENGTH_SHORT).show(); }
    }
    void startCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){status.setText("Camera permission is required.");return;}ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);f.addListener(()->{try{ProcessCameraProvider cp=f.get();Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(Executors.newSingleThreadExecutor(),image->{InputImage ii=InputImage.fromMediaImage(image.getImage(),image.getImageInfo().getRotationDegrees());scanner.process(ii).addOnSuccessListener(bs->{for(com.google.mlkit.vision.barcode.common.Barcode x:bs){String v=x.getRawValue();if(v!=null&&!v.isEmpty()){runOnUiThread(()->find(v));break;}}}).addOnCompleteListener(x->image.close());});cp.unbindAll();activeCamera=cp.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);status.setText("Camera ready — scan the product GTIN.");}catch(Exception e){status.setText("Camera error: "+e.getMessage());}},ContextCompat.getMainExecutor(this));}
    void stopCamera(){try{if(analysis!=null)analysis.clearAnalyzer(); if(activeCamera!=null)activeCamera.getCameraControl().enableTorch(false);}catch(Exception ignored){}activeCamera=null;analysis=null;status.setText("Camera stopped.");}
    void find(String v){String key=(v==null?"":v).trim();input.setText(key);notFound.setVisibility(View.GONE);result.setVisibility(View.GONE);if(key.isEmpty())return;Product p=products.get(key);if(p==null){notFound.setText("Product not found\n\nNo matching pbarcode_canonical was found in this catalog.\n\nGTIN: "+key);notFound.setVisibility(View.VISIBLE);return;}last=p;stopCamera();cameraCard.setVisibility(View.GONE);manualPanel.setVisibility(View.GONE);resultScroll.setVisibility(View.VISIBLE);info.setText("GTIN / pbarcode_canonical:  "+p.gtin+"\nWMS barcode:  "+p.wms+"\nPartner ID:  "+p.partner+"\nStatus:  "+p.status);try{qr.setImageBitmap(makeQr(p.wms,800));}catch(Exception e){status.setText("QR error: "+e.getMessage());}resultScroll.post(()->resultScroll.requestFocus());}
    Bitmap makeQr(String text,int size)throws WriterException{BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}
    void saveQr(){if(last==null)return;Bitmap b=makeQr(last.wms,1000);String name="QR-"+last.wms+".png";ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/GTIN-WMS-QR");try{android.net.Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);try(OutputStream o=getContentResolver().openOutputStream(u)){b.compress(Bitmap.CompressFormat.PNG,100,o);}Toast.makeText(this,"QR saved to Pictures/GTIN-WMS-QR",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void printQr(){if(last==null)return;PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("GTIN-WMS-QR-"+last.wms,new QrPrintAdapter(this,qr.getDrawable()),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText("Camera permission denied. You can still enter a GTIN manually.");}
}
