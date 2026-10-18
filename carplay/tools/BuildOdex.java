/** Build-only entry: optimize a DEX in a separate VM; never load OEM classes. */
public final class BuildOdex {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("input/output required");
        dalvik.system.DexFile file=dalvik.system.DexFile.loadDex(args[0],args[1],0);
        file.close();
        System.out.println("NATIVE_CACHE_BUILT");
    }
}
