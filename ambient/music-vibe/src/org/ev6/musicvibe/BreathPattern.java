package org.ev6.musicvibe;
final class BreathPattern {
 static long period(){return Options.period;}
 static int brightness(long elapsed){double phase=(elapsed%period())/(double)period();return 2+(int)Math.round(2*(1-Math.cos(2*Math.PI*phase)));}
 static int colorIndex(long elapsed,int first){return (int)((first+elapsed/period())%Palette.COLORS.length);}
 static int initialIndex(Lights s){for(int n=0;n<Palette.COLORS.length;n++){int[] c=Palette.COLORS[n];if(c[0]==s.r&&c[1]==s.g&&c[2]==s.b)return n;}return 0;}
}
