package io.opentakserver.opentakicu;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.util.Log;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import io.opentakserver.opentakicu.contants.Preferences;
import io.opentakserver.opentakicu.cot.Contact;
import io.opentakserver.opentakicu.cot.Detail;
import io.opentakserver.opentakicu.cot.Point;
import io.opentakserver.opentakicu.cot.Status;
import io.opentakserver.opentakicu.cot.TakControl;
import io.opentakserver.opentakicu.cot.TakProtocolSupport;
import io.opentakserver.opentakicu.cot.Takv;
import io.opentakserver.opentakicu.cot.auth;
import io.opentakserver.opentakicu.cot.Cot;
import io.opentakserver.opentakicu.cot.event;
import io.opentakserver.opentakicu.cot.__group;
import io.opentakserver.opentakicu.cot.precisionlocation;
import io.opentakserver.opentakicu.cot.uid;
import io.opentakserver.opentakicu.parser.CoT;
import io.opentakserver.opentakicu.utils.CertificateEnrollment;
import io.opentakserver.opentakicu.utils.Constants;
import io.opentakserver.opentakicu.utils.PEMImporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class TcpClient implements Runnable, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = Constants.TAG_PREFIX + TcpClient.class.getSimpleName();
    public static final String TAK_SERVER_CONNECTED = "tak_server_connected";
    public static final String TAK_SERVER_DISCONNECTED = "tak_server_disconnected";

    private SharedPreferences prefs;

    public String serverAddress;
    public int port;
    private boolean atak_auth = false;
    private String atak_username;
    private String atak_password;
    private boolean atak_ssl = false;
    private boolean atak_cert_enrollment = true;
    private String atak_trust_store;
    private String atak_trust_store_password;
    private String atak_client_cert;
    private String atak_client_cert_password;
    private String uid;
    private String path;
    private String callsign;

    private Socket socket;
    private Context context;
    private Intent batteryStatus;
    private OnMessageReceived mMessageListener;
    public boolean mRun = false;
    private OutputStream mBufferOut;
    private InputStream mBufferIn;
    private Thread pingSenderThread;
    private XmlMapper xmlMapper;

    public class TAKServerConnectionException extends Exception {
        private final String message;
        public TAKServerConnectionException(final String message) {
            this.message = message;
        }
    }
    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(Context context, String serverAddress, int port, OnMessageReceived listener) {
        this.context = context;
        this.serverAddress = serverAddress;
        this.port = port;
        mMessageListener = listener;

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, ifilter);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        XmlFactory xmlFactory = XmlFactory.builder()
                .xmlInputFactory(new WstxInputFactory())
                .xmlOutputFactory(new WstxOutputFactory())
                .build();

        xmlMapper = XmlMapper.builder(xmlFactory).build();
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        getSettings();
    }

    private void connect() throws TAKServerConnectionException {
        try {
            Log.d(TAG, "Connecting...");
            getSettings();

            if (atak_ssl) {
                SSLSocketFactory factory = null;
                if (atak_cert_enrollment) {
                    Log.d(TAG, "Connecting via SSL with certificate enrollment");
                    String certsDir = context.getFilesDir() + "/certs";
                    String clientPemPath = certsDir + "/client.pem";
                    String serverPemPath = certsDir + "/server.pem";

                    File clientPem = new File(clientPemPath);
                    File serverPem = new File(serverPemPath);

                    if (!clientPem.exists() || !serverPem.exists()) {
                        if (atak_username == null || atak_username.isEmpty() || atak_password == null || atak_password.isEmpty()) {
                            throw new TAKServerConnectionException("Certificate enrollment requires a username and password. Enable authentication and set credentials in ATAK settings.");
                        }
                        Log.i(TAG, "Certificates not found, starting enrollment");
                        CertificateEnrollment enrollment = new CertificateEnrollment(context, serverAddress, atak_username, atak_password);
                        if (!enrollment.enroll(clientPemPath, serverPemPath)) {
                            throw new TAKServerConnectionException("Certificate enrollment failed for server: " + serverAddress);
                        }
                    }

                    factory = PEMImporter.createSSLFactory(new File(clientPemPath), new File(serverPemPath), "atakatak");
                } else {
                    Log.d(TAG, "Connecting via SSL with manual certificates");
                    KeyStore trusted = KeyStore.getInstance("PKCS12");
                    FileInputStream trust_store = new FileInputStream(atak_trust_store);

                    KeyStore client_cert_keystore = KeyStore.getInstance("PKCS12");
                    FileInputStream client_cert = new FileInputStream(atak_client_cert);

                    trusted.load(trust_store, atak_trust_store_password.toCharArray());
                    trust_store.close();

                    client_cert_keystore.load(client_cert, atak_client_cert_password.toCharArray());
                    client_cert.close();

                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(trusted);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(client_cert_keystore, atak_client_cert_password.toCharArray());
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
                    sslContext.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

                    factory = sslContext.getSocketFactory();
                }

                if (factory == null) {
                    Log.e(TAG, "SSL Socket Factory is NULL");
                    throw new TAKServerConnectionException("Unable to connect to server: " + serverAddress + ":" + port);
                }

                Log.d(TAG, "Connecting to " + serverAddress + ":" + port);
                socket = factory.createSocket(serverAddress, port);

            } else {
                Log.d(TAG, "Connecting via TCP");
                socket = new Socket(serverAddress, port);
                socket.setSoTimeout(1000);
                Log.d(TAG, "Connected via TCP");
            }

            mBufferOut = socket.getOutputStream();
            mBufferIn = socket.getInputStream();
            context.sendBroadcast(new Intent(TAK_SERVER_CONNECTED).setPackage(context.getPackageName()));

            // Send out the version information about what we support as a client
            event versionEvent = new event();
            versionEvent.setUid("protouid");
            versionEvent.setType(Constants.CT_TAKP_V);
            versionEvent.setPoint(new Point(0.0, 0.0, 0.0));
            Detail detail = new Detail(new TakControl(new TakProtocolSupport()));
            versionEvent.setDetail(detail);
            sendMessage(xmlMapper.writeValueAsString(versionEvent));
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            stopClient();
        }
    }
    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public void sendMessage(final String message) {
        try {
            Runnable runnable = () -> {
                try {
                    if (mBufferOut != null && !socket.isClosed()) {
                        mBufferOut.write(message.getBytes(Charset.defaultCharset()));
                        mBufferOut.flush();
                    } else {
                        Log.i(TAG, "No Connection to TAK Server");
                    }
                } catch(SocketException ex) {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    Log.e(TAG, "SocketException " + ex.getMessage() + ": " + sw);
                    stopClient();
                    stopPing();
                } catch (IOException ex) {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    Log.e(TAG, "IOException " + ex.getMessage() + ": " + sw);
                }
            };
            Thread thread = new Thread(runnable);
            thread.start();
        } catch(Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "UnknownException " + ex.getMessage() + ": " + sw);
        }
    }

    /**
     * Close the connection and release the members
     */
    public void stopClient() {
        mRun = false;

        if(mBufferIn != null) {
            try {
                mBufferIn.close();
            } catch(Exception ex) {

            }
        }
        if (mBufferOut != null) {
            try {
                mBufferOut.flush();
            } catch(IOException ex) {
                //DO nothing
            }

            try {
                mBufferOut.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close buffer", e);
            }
        }

        stopPing();

        prefs.unregisterOnSharedPreferenceChangeListener(this);
        mMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
        socket = null;
        Log.d(TAG, "Sending Broadcast " + TAK_SERVER_DISCONNECTED);
        context.sendBroadcast(new Intent(TAK_SERVER_DISCONNECTED).setPackage(context.getPackageName()));
    }

    @Override
    public void run() {
        try {
            connect();

            if (atak_auth) {
                Cot cot = new Cot(atak_username, atak_password, uid);
                auth atakAuth = new auth(cot);
                sendMessage(xmlMapper.writeValueAsString(atakAuth));
            }

            try {
                byte[] buffer = new byte[1024];
                int read;
                StringBuilder sb = new StringBuilder();
                while (!Thread.currentThread().isInterrupted()) {
                    while ((read = mBufferIn.read(buffer)) != -1) {
                        sb.append(new String(buffer, 0, read));
                        if (sb.toString().contains(Constants.END_EVENT)) {
                            processCoT(sb.toString());
                            sb.setLength(0);    // Reset builder
                        }
                    }
                }
            } catch(IOException ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Log.e(TAG, "IOException " + ex.getMessage() + ": " + sw);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            stopClient();
        }
    }

    private void processCoT(final String message) {
        CoT cot = new CoT(message);
        Log.d(TAG, "Received: " + cot.getType());

        switch(cot.getType()) {
            case Constants.CT_TAKP_V:
                Log.i(TAG, "Processing Version String");
                sendClientAnnouncement();
                startPing();
                break;
            case Constants.CT_CONN_RESPONSE:
                Log.i(TAG, "Received Pong");
                break;
            default:
                if (mMessageListener != null && message != null) {
                    mMessageListener.messageReceived(message);
                }
                break;
        }
    }

    private void sendClientAnnouncement() {
        try {
            event announcement = new event();
            announcement.setUid(uid);
            announcement.setType("b-m-p-s-p-loc");
            announcement.setPoint(new Point(9999999, 9999999, 9999999));

            Contact contact = new Contact(callsign, "*:-1:stcp");
            Takv takv = new Takv(context);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float) scale;

            Detail detail = new Detail(contact, null, null, null, takv, new uid(callsign), null, new Status(batteryPct));
            detail.setPrecisionlocation(new precisionlocation("UNKNOWN", "UNKNOWN"));
            announcement.setDetail(detail);

            Log.i(TAG, "Sending client announcement with callsign: " + callsign);
            sendMessage(xmlMapper.writeValueAsString(announcement));
        } catch (JsonProcessingException ex) {
            Log.e(TAG, "Failed to send client announcement", ex);
        }
    }

    private String getPing() throws JsonProcessingException {
        event takPing = new event();
        takPing.setType(Constants.CT_CONN_REQUEST);
        takPing.setUid("takPing");
        takPing.setPoint(new Point(0.0, 0.0,0.0));
        return xmlMapper.writeValueAsString(takPing);
    }
    /**
     * startPing - this sends a ping to the TAK Server every 30 seconds to let it konw
     *   we are still active
     */
    private void startPing() {
        try {
            sendMessage(getPing());

            Runnable runnable = () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(30000);
                        Log.i(TAG, "Sending Ping");
                        sendMessage(getPing());
                    } catch(JsonProcessingException ex) {
                        StringWriter sw = new StringWriter();
                        ex.printStackTrace(new PrintWriter(sw));
                        Log.e(TAG,"Caught JsonProcessingException: " + ex.getMessage() + " - " + sw);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            pingSenderThread = new Thread(runnable);
            pingSenderThread.start();
        } catch(JsonProcessingException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG,"Caught JsonProcessingException: " + ex.getMessage() + " - " + sw);
        }
    }

    private void stopPing() {
        if(pingSenderThread != null) {
            pingSenderThread.interrupt();
            pingSenderThread = null;
        }
    }

    public void setmRun(boolean mRun) {
        if (!mRun) {
            Log.d(TAG, "Stopping thread");
            stopClient();
        }
        this.mRun = mRun;
    }

    private void getSettings() {
        serverAddress = prefs.getString(Preferences.ATAK_SERVER_ADDRESS, Preferences.ATAK_SERVER_ADDRESS_DEFAULT);
        port = Integer.parseInt(prefs.getString(Preferences.ATAK_SERVER_PORT, Preferences.ATAK_SERVER_PORT_DEFAULT));
        atak_auth = prefs.getBoolean(Preferences.ATAK_SERVER_AUTHENTICATION, Preferences.ATAK_SERVER_AUTHENTICATION_DEFAULT);
        atak_username = prefs.getString(Preferences.ATAK_SERVER_USERNAME, Preferences.ATAK_SERVER_USERNAME_DEFAULT);
        atak_password = prefs.getString(Preferences.ATAK_SERVER_PASSWORD, Preferences.ATAK_SERVER_PASSWORD_DEFAULT);
        atak_ssl = prefs.getBoolean(Preferences.ATAK_SERVER_SSL, Preferences.ATAK_SERVER_SSL_DEFAULT);
        atak_cert_enrollment = prefs.getBoolean(Preferences.ATAK_CERT_ENROLLMENT, Preferences.ATAK_CERT_ENROLLMENT_DEFAULT);
        atak_trust_store = prefs.getString(Preferences.ATAK_SERVER_SSL_TRUST_STORE, Preferences.ATAK_SERVER_SSL_TRUST_STORE_DEFAULT);
        atak_trust_store_password = prefs.getString(Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD, Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD_DEFAULT);
        atak_client_cert = prefs.getString(Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE, Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_DEFAULT);
        atak_client_cert_password = prefs.getString(Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_PASSWORD, Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_PASSWORD_DEFAULT);
        uid = prefs.getString(Preferences.UID, Preferences.UID_DEFAULT);
        path = prefs.getString(Preferences.STREAM_PATH, Preferences.STREAM_PATH_DEFAULT);
        callsign = prefs.getString(Preferences.ATAK_CALLSIGN, Preferences.ATAK_CALLSIGN_DEFAULT);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        getSettings();
    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the Activity
    //class at on AsyncTask doInBackground
    public interface OnMessageReceived {
        public void messageReceived(String message);
    }

}
