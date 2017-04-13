package edu.buffalo.cse.cse486586.groupmessenger2;

import android.util.Log;

import java.util.Comparator;

// a class to store message object
public class Message {
    String msg;
    float SeqNo;
    boolean deliverable;
    String msgType;
    String origin;
    String sender;
    String msgUID;
    final String delimiter= "::::::::";
    //constructor for message class
    Message(String msg, float SeqNo, boolean deliverable, String msgType, String origin, String sender, String msgUID) {
        this.deliverable = deliverable;
        this.msg = msg;
        this.msgType = msgType;
        this.SeqNo = SeqNo;
        this.origin=origin;
        this.sender=sender;
        this.msgUID=msgUID;
    }
    //constructor for splitting the passed string
    Message(String str){
        String[] parts=str.split(delimiter);
        //Log.d("length",String.valueOf(parts.length));
        this.msg=parts[0];
        this.SeqNo= Float.parseFloat(parts[1]);
        this.deliverable= Boolean.parseBoolean(parts[2]);
        this.msgType=parts[3];
        this.origin=parts[4];
        this.sender=parts[5];
        this.msgUID=parts[6];
    }
    //constructor
    Message(String msgType, String origin, String sender, String msgUID){
        this.msg="blank";
        this.deliverable=false;
        this.SeqNo=0;
        this.msgUID=msgUID;
        this.msgType=msgType;
        this.origin=origin;
        this.sender=sender;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public float getSeqNo() {
        return SeqNo;
    }

    public void setSeqNo(float seqNo) {
        SeqNo = seqNo;
    }

    public boolean isDeliverable() {
        return deliverable;
    }

    public void setDeliverable(boolean deliverable) {
        this.deliverable = deliverable;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMsgUID() {
        return msgUID;
    }

    public void setMsgUID(String msgUID) {
        this.msgUID = msgUID;
    }

    public String messageToString(){
        String toReturn=msg+delimiter+SeqNo+delimiter+deliverable+delimiter+msgType+delimiter+origin+delimiter+sender+delimiter+msgUID;
        return toReturn;
    }






}


