package com.example.vpnapp;

import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor vpnInterface;
    private String ServerIP;
    private int ServerPortNumber;
    // handler - класс в Android, который используется для выполнения кода в определенном потоке. Он позволяет вам
    // отправлять и обрабатывать сообщения и задачи в определенный поток. В данном случае, вы создаете Handler,
    // связанный с основным потоком UI. Looper.getMainLooper() - это статический метод класса Looper, который
    // возвращает объект Looper для основного потока пользовательского интерфейса (UI-потока).
    // Looper - это механизм, который позволяет потоку ожидать и обрабатывать сообщения и задачи в бесконечном цикле.
    private Handler handler = new Handler(Looper.getMainLooper());
    public static MyVpnService instance;
    public static final String ACTION_VPN_CONNECTED = "com.wkdeveloper.vpn.Services";
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    public ParcelFileDescriptor getVpnInterface() {
        return vpnInterface;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null){
            ServerIP = intent.getStringExtra("vpnIp");
            ServerPortNumber = intent.getIntExtra("vpnPort", 0);

            // Создается новый поток (Thread) с помощью анонимного класса Runnable. Внутри этого класса реализован
            // метод run(). В этом методе вызывается runVpnConnection(). Поскольку выполнение операций VPN может
            // занимать некоторое время, их запускают в отдельном потоке. return START_STICKY: Эта инструкция указывает
            // системе Android на то, что службу следует рассматривать как "стартующую службу" (START_STICKY).
            // Это означает, что если служба завершается (например, из-за нехватки памяти), она будет перезапущена
            // автоматически, когда станут доступны ресурсы.
            Thread VpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    MyVpnService.this.runVpnConnection();
                }
            });
            VpnThread.start();
        }
        return START_STICKY;
    } // ок
    private void runVpnConnection() {
        try{
            // устанавливается VPN соединение
            if(establishedVpnConnection()){
                readFromVpnInterface();
            }
        } catch (Exception e) {
            Log.e(TAG, "error during vpn connection: " + e.getMessage());
        } finally {
            stopVpnConnection();
        }
    } // ок
    private boolean establishedVpnConnection() throws IOException {
        if(vpnInterface != null){
            // Создается объект Builder для настройки параметров VPN-подключения.
            Builder builder = new Builder();
            // С помощью builder.addAddress(ServerIP, 32) указывается адрес сервера
            // VPN (в данном случае, ServerIP) и его префиксная длина (32 бита для одного IP-адреса).
            builder.addAddress(ServerIP, 32);
            // С помощью builder.addRoute("0.0.0.0", 0) добавляется маршрут по умолчанию через VPN-подключение.
            builder.addRoute("0.0.0.0", 0);
            // устанавливается сессия VPN, используя имя вашего приложения из ресурсов.
            vpnInterface = builder.setSession(getString(R.string.app_name))
                    // указывает, что нет необходимости в интенте для настройки VPN.
                    .setConfigureIntent(null)
                    // устанавливается VPN-подключение.
                    .establish();
            return vpnInterface != null;
        }else{
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onVpnConnectionSuccess();
                    // выводится короткое сообщение (Toast), уведомляющее пользователя о том, что VPN-соединение уже установлено.
                    Toast.makeText(MyVpnService.this, "Vpn Connection Already Established", Toast.LENGTH_SHORT).show();
                }
            });
        }
        return true;
    } // ок
    private void readFromVpnInterface() throws IOException{
        isRunning.set(true);
        // Создается буфер ByteBuffer размером 32767 байт для временного хранения данных, полученных из VPN-интерфейса.
        ByteBuffer buffer = ByteBuffer.allocate(32767);
        while (isRunning.get()){
            try{
                // Создается объект FileInputStream для чтения данных из файлового дескриптора VPN-интерфейса.
                // Файловый дескриптор vpnInterface.getFileDescriptor() представляет собой способ получения доступа
                // к данным, поступающим из VPN-интерфейса.
                FileInputStream InputStream = new FileInputStream(vpnInterface.getFileDescriptor());
                // Данные считываются из InputStream в буфер buffer, и в переменной length сохраняется количество считанных байт.
                int length = InputStream.read(buffer.array());
                if(length > 0){
                    // Если были считаны данные, они преобразуются в строку с использованием
                    // String receivedData = new String(buffer.array(), 0, length);. Текстовые данные, полученные
                    // из VPN-интерфейса, сохраняются в переменной receivedData.
                    String receivedData = new String(buffer.array(), 0, length);
                    // Создается Intent с названием "received_data_from_vpn" и добавляется дополнительная информация
                    // в виде полученных данных: intent.putExtra("data", receivedData);.
                    Intent intent = new Intent("received_data_from_vpn");
                    intent.putExtra("data", receivedData);
                    // С помощью LocalBroadcastManager.getInstance(this).sendBroadcast(intent); отправляется
                    // широковещательное сообщение (broadcast message) с полученными данными.
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    // вызывается метод writeToNetwork(buffer, length);, который, вероятно, предназначен для
                    // записи обработанных данных обратно в сеть.
                    writeToNetwork(buffer, length);
                }
            }catch (Exception e){
                Log.e(TAG, "error reading data from vpn interface: " + e.getMessage());
            }
        }
    } // ок
    private void writeToNetwork(ByteBuffer buffer, int length) {
        // Данные из буфера buffer преобразуются в строку processData. Предполагается, что buffer содержит
        // обработанные данные которые нужно отправить на сервер.
        String processData = new String(buffer.array(), length);
        try{
            // Создается новый сокет (Socket) для соединения с удаленным сервером.
            Socket socket = new Socket(ServerIP, ServerPortNumber);
            // Получается выходной поток (OutputStream) для сокета. Этот поток будет использоваться для отправки данных на сервер.
            OutputStream outputStream = socket.getOutputStream();
            // Строка processData преобразуется в массив байтов dataBytes, используя кодировку UTF-8. Это делается
            // для того, чтобы отправить данные в виде байтов на сервер.
            byte[] dataBytes = processData.getBytes(StandardCharsets.UTF_8);
            // Данные в виде байтов из dataBytes отправляются на сервер с использованием выходного потока outputStream.
            outputStream.write(dataBytes);
            // После отправки данных, выходной поток и сокет закрываются
            outputStream.close();
            socket.close();

        }catch(Exception e) {
            Log.e(TAG, "error sending data to the server: " + e.getMessage());
        }
    } // ок
    @Override
    public void onDestroy() {
        // деструктор класса
        super.onDestroy();
        stopVpnConnection();
    } // ок
    private void stopVpnConnection() {
        // Закрытие VPN соединения
        isRunning.set(false);
        if(vpnInterface != null){
            try{
                vpnInterface.close();
            }catch(Exception e) {
                Log.e(TAG, "error of closing vpn interface: " + e.getMessage());
            }
        }
    } // ок
    private void onVpnConnectionSuccess(){
        Intent intent = new Intent(ACTION_VPN_CONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    } // ок
}
