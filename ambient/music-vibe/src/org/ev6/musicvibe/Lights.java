package org.ev6.musicvibe;
import org.json.*;
final class Lights {
 final int r,g,b,bright,dim,drive,fade;
 Lights(int R,int G,int B,int L,int D,int M,int F){r=R;g=G;b=B;bright=L;dim=D;drive=M;fade=F;}
 static Lights decode(byte[] b,int fade)throws Exception {if(b==null||b.length!=9)throw new Exception("No valid light response");Lights s=new Lights(((b[0]&255)<<8)|(b[1]&255),((b[2]&255)<<8)|(b[3]&255),((b[4]&255)<<8)|(b[5]&255),b[6]&255,b[7]&255,b[8]&255,fade);if(s.r>511||s.g>511||s.b>511||s.bright<1||s.bright>11||s.dim<1||s.dim>2||s.drive<1||s.drive>2)throw new Exception("Light response outside verified range");return s;}
 JSONObject json()throws Exception {JSONObject j=new JSONObject();j.put("red",r);j.put("green",g);j.put("blue",b);j.put("brightness_raw",bright);j.put("dim_raw",dim);j.put("drive_raw",drive);j.put("fade_raw",fade);return j;}
 static Lights from(JSONObject j)throws Exception {return new Lights(j.getInt("red"),j.getInt("green"),j.getInt("blue"),j.getInt("brightness_raw"),j.getInt("dim_raw"),j.getInt("drive_raw"),j.getInt("fade_raw"));}
 boolean matches(byte[] d){try{Lights o=decode(d,fade);return r==o.r&&g==o.g&&b==o.b&&bright==o.bright&&dim==o.dim&&drive==o.drive;}catch(Exception e){return false;}}
}
