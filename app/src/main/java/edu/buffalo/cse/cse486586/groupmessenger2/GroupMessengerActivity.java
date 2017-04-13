package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static ArrayList<String> REMOTE_PORTS=new ArrayList<String>();
    static final int SERVER_PORT = 10000;
    static int messageCounter=0;
    static int ProposedMsgSequence=0;
    static int UID=0;
    static String myPort;
    static Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static boolean hasStarted = false;
    static ArrayList<String> agreedAlready = new ArrayList<String>();



    private static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    //Priority Queue to add all the received message objects and then remove and print once a sequence number is agreed upon
    PriorityBlockingQueue<Message> messagePriorityQueue= new PriorityBlockingQueue<Message>(11, new Comparator<Message>() {
        @Override
        public int compare(Message m1, Message m2) {
            if(m1.SeqNo < m2.SeqNo)
                return -1;
            else
                return 1;
        }
    });
    //ArrayList to store messages, that we'll later remove from queue, once we have agreed Sequence No
    ArrayList<Message> messageArrayList=new ArrayList<Message>();
    //hashmap to check if proposed sequence from all AVD's received on a unique msdUID
    HashMap<String, ArrayList<Message>> recievedAll=new HashMap<String, ArrayList<Message>>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        REMOTE_PORTS.add("11108");
        REMOTE_PORTS.add("11112");
        REMOTE_PORTS.add("11116");
        REMOTE_PORTS.add("11120");
        REMOTE_PORTS.add("11124");

        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            serverSocket.setReuseAddress(true);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            /*
            * http://stackoverflow.com/questions/11434056/how-to-run-a-method-every-x-seconds
            * 2nd answer
            */
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(hasStarted){
                        while(true){
                            final Message msgToPrint=messagePriorityQueue.poll();
                            if(msgToPrint==null)
                                break;
                            if(!REMOTE_PORTS.contains(msgToPrint.getOrigin()))
                                continue;
                            if(msgToPrint.deliverable==false){
                                messagePriorityQueue.add(msgToPrint);
                                break;
                            }
                            /*
                            * http://stackoverflow.com/questions/5161951/android-only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-vi
                            * 2nd asnwer
                            */
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(!agreedAlready.contains(msgToPrint.getMsgUID())) {
                                        TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                                        remoteTextView.append(msgToPrint.getMsg() + "\n");
                                        ContentValues contentValues = new ContentValues();
                                        contentValues.put("key", Integer.toString(messageCounter));
                                        contentValues.put("value", msgToPrint.getMsg() + " : " + String.valueOf(msgToPrint.getSeqNo()));
                                        getContentResolver().insert(mUri, contentValues);
                                        messageCounter++;
                                        agreedAlready.add(msgToPrint.getMsgUID());
                                    }
                                }
                            });
//                            publishMessage(msgToPrint);
                        }
                        if(REMOTE_PORTS.size() == 5){
                            Message blank=new Message("blank", myPort, "", myPort+(UID++));
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, blank);
                        }
                    }
                }
            }, 11000, 5000);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView text = (TextView) findViewById(R.id.editText1);
                String msg = text.getText().toString();
                float SeqNo= (float) (ProposedMsgSequence);
                Message message=new Message(msg, SeqNo, false,"NewMsg", myPort, myPort, myPort+(UID++));
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);
                text.setText("");
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ClientTask extends AsyncTask<Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... msgs) {
                Message msgToSend = msgs[0];
                if(msgToSend.getMsgType().equals("ProposedMsg")){
                    try{
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(msgToSend.getOrigin()));
                        DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                        dataOut.writeUTF(msgToSend.messageToString());
                        DataInputStream dataIn=new DataInputStream(socket.getInputStream());
                        String response=dataIn.readUTF();
                        if(response.equals("ack")) {
                            socket.close();
                        }
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException " );
                        faultManage(msgToSend.getOrigin());
                        e.printStackTrace();
                    }
                }
                else if(msgToSend.getMsgType().equals("NewMsg")|| msgToSend.getMsgType().equals("AgreedMsg") || msgToSend.getMsgType().equals("blank")){
                    for (String remotePort : REMOTE_PORTS) {
                        try{
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remotePort));
                            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                            dataOut.writeUTF(msgToSend.messageToString());
                            DataInputStream dataIn=new DataInputStream(socket.getInputStream());
                            String response=dataIn.readUTF();
                            if(response.equals("ack") && msgToSend.getMsgType().equals("blank")) {
                                socket.close();
                            }
                        } catch (UnknownHostException e) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e) {
                            Log.e(TAG, "ClientTask socket IOException " + remotePort );
                            faultManage(remotePort);
                            e.printStackTrace();
                        }

                    }
                }

            return null;
        }
        public void faultManage(String port) {
            //Remove messages from Priority queue sent by crashed port
            Message msgToRemove;
            for (int i = 0; i < messageArrayList.size(); i++) {
                msgToRemove = (Message) messageArrayList.get(i);
                if (msgToRemove.getOrigin().equals(port)) {
                    messagePriorityQueue.remove(msgToRemove);
                    recievedAll.remove(msgToRemove.getMsgUID());
                }
            }

            //remove all received proposed Sequence from all received msgs for the crashed port
            ArrayList<String> temp = new ArrayList<String>();
            for(String remPort : REMOTE_PORTS){
                if (!remPort.equals(port)){
                    temp.add(remPort);
                }
            }
            REMOTE_PORTS = temp;

            for (String msgUID : recievedAll.keySet()) {
                ArrayList<Message> msgList = recievedAll.get(msgUID);
                if (msgList.size() == REMOTE_PORTS.size() + 1)
                    continue;
                if (msgList.size() == REMOTE_PORTS.size()) {
                    boolean waitingForCrashed = true;
                    for (int i = 0; i < msgList.size(); i++) {
                        if (((Message) msgList.get(i)).getSender().equals(port)){
                            waitingForCrashed = false;
                            break;
                        }

                    }
                    if(waitingForCrashed){
                        float max=0;
                        for(int i=0;i<msgList.size();i++){
                            if(max<((Message)msgList.get(i)).getSeqNo())
                                max=((Message)msgList.get(i)).getSeqNo();
                        }
                        Message recieved = msgList.get(0);
                        recieved.setSeqNo(max);
                        recieved.setMsgType("AgreedMsg");
                        doInBackground(recieved);
                    }
                }
                recievedAll.put(msgUID,msgList);
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket socket = null;
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri mUri = uriBuilder.build();
            try {
                while(true){
                    socket = serverSocket.accept();
                    DataInputStream dataIn = new DataInputStream(socket.getInputStream());
                    String recievedMsg = dataIn.readUTF();
                    DataOutputStream dataOut=new DataOutputStream(socket.getOutputStream());
                    dataOut.writeUTF("ack");
                    Message recieved=new Message(recievedMsg);
                    if(recieved.getMsgType().equals("NewMsg")) {
                        recieved.setSeqNo((float)(++ProposedMsgSequence+0.1*((Integer.parseInt(myPort)-11104)/4)));
                        recieved.setMsgType("ProposedMsg");
                        recieved.setSender(myPort);
                        messagePriorityQueue.add(recieved);
                        messageArrayList.add(recieved);
                        publishProgress(recieved);
                    }
                    else if(recieved.getMsgType().equals("ProposedMsg")){
                        if(!recievedAll.containsKey(recieved.getMsgUID())){
                            recievedAll.put(recieved.getMsgUID(),new ArrayList<Message>());
                        }
                        ArrayList temp=recievedAll.get(recieved.getMsgUID());
                        temp.add(recieved);
                        if(temp.size()>= REMOTE_PORTS.size()){
                            float max=0;
                            for(int i=0;i<temp.size();i++){
                                if(max<((Message)temp.get(i)).getSeqNo())
                                    max=((Message)temp.get(i)).getSeqNo();
                            }
                            recieved.setSeqNo(max);
                            recieved.setMsgType("AgreedMsg");
                            publishProgress(recieved);

                        }
                        recievedAll.put(recieved.getMsgUID(),temp);
                    }
                    else if(recieved.getMsgType().equals("AgreedMsg")){
                        recieved.setMsgType("FinalMsg");
                        recieved.setDeliverable(true);
                        Message finalMsg;
                        for(int i=0;i<messageArrayList.size();i++){
                            finalMsg=(Message)messageArrayList.get(i);
                            if(finalMsg.getMsgUID().equals(recieved.getMsgUID())){
                                messagePriorityQueue.remove(finalMsg);
                                break;
                            }
                        }
                        messagePriorityQueue.add(recieved);
                        hasStarted = true;
                        Log.d("agreed",recieved.getMsg());
                        publishProgress(recieved);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

            return null;
        }

        protected void onProgressUpdate(Message...msgs) {
            /*
             * The following code displays what is received in doInBackground().
             */
            Message msgReceived = msgs[0];
            if(msgReceived.getMsgType().equals("ProposedMsg")){
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgReceived);
            }
            if(msgReceived.getMsgType().equals("AgreedMsg")){
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgReceived);
            }
            return;
        }
    }
}

