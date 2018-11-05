package com.example.scarx.idcardreader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.zkteco.android.IDReader.IDPhotoHelper;
import com.zkteco.android.IDReader.WLTService;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.core.utils.LogHelper;
import com.zkteco.android.biometric.module.idcard.IDCardReader;
import com.zkteco.android.biometric.module.idcard.IDCardReaderFactory;
import com.zkteco.android.biometric.module.idcard.exception.IDCardReaderException;
import com.zkteco.android.biometric.module.idcard.meta.IDCardInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    private static final int VID = 1024;    //IDR VID
    private static final int PID = 50010;     //IDR PID
    private IDCardReader idCardReader = null;
    private TextView textView = null;
    private ImageView imageView = null;
    private boolean bopen = false;
    private boolean bStoped = false;
    private int mReadCount = 0;
    private CountDownLatch countdownLatch = new CountDownLatch(1);

    private Context mContext = null;
    private UsbManager musbManager = null;
    private final String ACTION_USB_PERMISSION = "com.example.scarx.idcardreader.USB_PERMISSION";

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                    }
                    else
                    {
                        Toast.makeText(mContext, "USB未授权", Toast.LENGTH_SHORT).show();
                        //mTxtReport.setText("USB未授权");
                    }
                }
            }
        }
    };

    private void RequestDevicePermission()
    {
        musbManager = (UsbManager)this.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        mContext.registerReceiver(mUsbReceiver, filter);

        for (UsbDevice device : musbManager.getDeviceList().values())
        {
            if (device.getVendorId() == VID && device.getProductId() == PID)
            {
                Intent intent = new Intent(ACTION_USB_PERMISSION);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                musbManager.requestPermission(device, pendingIntent);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        textView = (TextView) findViewById(R.id.textView);
        imageView = (ImageView) findViewById(R.id.imageView);
        mContext = this.getApplicationContext();
        startIDCardReader();
    }

    public Context getContext()
    {
        return this.getApplicationContext();
    }

    private void startIDCardReader() {
        // Define output log level
        LogHelper.setLevel(Log.VERBOSE);
        // Start fingerprint sensor
        Map idrparams = new HashMap();
        idrparams.put(ParameterHelper.PARAM_KEY_VID, VID);
        idrparams.put(ParameterHelper.PARAM_KEY_PID, PID);
        idCardReader = IDCardReaderFactory.createIDCardReader(this, TransportType.USB, idrparams);
        //idCardReader.setLibusbFlag(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Destroy fingerprint sensor when it's not used
        IDCardReaderFactory.destroy(idCardReader);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public static void writeLogToFile(String log) {
        try {
            File dirFile = new File("/sdcard/zkteco/");  //目录转化成文件夹
            if (!dirFile.exists()) {              //如果不存在，那就建立这个文件夹
                dirFile.mkdirs();
            }
            String path = "/sdcard/zkteco/idrlog.txt";
            File file = new File(path);
            if (!file.exists()) {
                File dir = new File(file.getParent());
                dir.mkdirs();
                file.createNewFile();
            }
            FileOutputStream outStream = new FileOutputStream(file, true);
            log += "\r\n";
            outStream.write(log.getBytes());
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void OnBnBegin(View view) throws IDCardReaderException
    {
        try {
            if (bopen)
            {
                textView.setText("设备已连接");
                return;
            }
            RequestDevicePermission();
            idCardReader.open(0);
            bStoped = false;
            mReadCount = 0;
            writeLogToFile("连接设备成功");
            textView.setText("连接成功");
            bopen = true;
            new Thread(new Runnable() {
                public void run() {
                    while (!bStoped) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        final IDCardInfo idCardInfo = new IDCardInfo();
                        boolean ret = false;
                        final long nTickstart = System.currentTimeMillis();
                        try {
                            idCardReader.findCard(0);
                            idCardReader.selectCard(0);
                        }catch (IDCardReaderException e)
                        {
                            //continue;
                        }
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            ret = idCardReader.readCard(0, 0, idCardInfo);
                        }
                        catch (IDCardReaderException e)
                        {
                            writeLogToFile("读卡失败，错误信息：" + e.getMessage());
                        }
                        if (ret)
                        {
                            final long nTickUsed = (System.currentTimeMillis()-nTickstart);
                            writeLogToFile("读卡成功：" + (++mReadCount) + "次" + "，耗时：" + nTickUsed + "毫秒");
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    textView.setText("读取次数："  + mReadCount + ",耗时："+  nTickUsed +  "毫秒,姓名：" + idCardInfo.getName() + "，民族：" + idCardInfo.getNation() + "，住址：" + idCardInfo.getAddress() + ",身份证号：" + idCardInfo.getId());
                                    if (idCardInfo.getPhotolength() > 0) {
                                        byte[] buf = new byte[WLTService.imgLength];
                                        if (1 == WLTService.wlt2Bmp(idCardInfo.getPhoto(), buf)) {
                                            imageView.setImageBitmap(IDPhotoHelper.Bgr2Bitmap(buf));
                                        }
                                    }
                                }
                            });
                        }
                    }
                    countdownLatch.countDown();
                }
            }).start();
        }catch (IDCardReaderException e)
        {
            writeLogToFile("连接设备失败");
            textView.setText("连接失败");
            textView.setText("开始读卡失败，错误码：" + e.getErrorCode() + "\n错误信息：" + e.getMessage() + "\n内部代码=" + e.getInternalErrorCode());
        }
    }

    public void OnBnStop(View view)
    {
        if (!bopen)
        {
            return;
        }
        bStoped = true;
        mReadCount = 0;
        try {
            countdownLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            idCardReader.close(0);
        } catch (IDCardReaderException e) {
            e.printStackTrace();
        }
        textView.setText("设备断开连接");
        bopen = false;
    }
}
