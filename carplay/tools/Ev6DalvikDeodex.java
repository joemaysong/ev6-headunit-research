import java.io.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.analysis.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.iface.*;
import org.jf.baksmali.*;
public class Ev6DalvikDeodex {
 public static void main(String[] args) throws Exception {
  File input=new File(args[0]);
  MultiDexContainer<? extends DexBackedDexFile> c=DexFileFactory.loadDexContainer(input,Opcodes.forApi(19));
  MultiDexContainer.DexEntry<? extends DexBackedDexFile> e=c.getEntry(c.getDexEntryNames().get(0));
  List<String> dirs=Arrays.asList(args[1]);
  ClassPathResolver r=new ClassPathResolver(dirs,new ArrayList<String>(),e);
  BaksmaliOptions o=new BaksmaliOptions();o.apiLevel=19;o.deodex=true;
  o.inlineResolver=InlineMethodResolver.createInlineMethodResolver(((DexBackedOdexFile)e.getDexFile()).getOdexVersion());
  o.classPath=new ClassPath(r.getResolvedClassProviders(),false,ClassPath.NOT_ART);
  System.out.println("DALVIK_LAYOUT="+!o.classPath.isArt());
  boolean ok=Baksmali.disassembleDexFile(e.getDexFile(),new File(args[2]),4,o);
  if(!ok)System.exit(2);
 }
}
