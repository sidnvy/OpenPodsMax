package dev.airpodsnative.companion;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Minimal Java translation of CAPod's HiddenApiBypass, based on LSPosed's
 * AndroidHiddenApiBypass (Apache-2.0, copyright LSPosed 2021).
 * Only used to gain access to private BluetoothDevice APIs *within the calling
 * app process*, not to obtain system/root permissions.
 * Original:
 * https://github.com/d4rken-org/capod
 * https://github.com/LSPosed/AndroidHiddenApiBypass
 */
final class ArtBypass {
    private ArtBypass(){}

    @SuppressWarnings("unused")
    public static class AccessibleObjectMirror {
        private boolean override;
    }
    @SuppressWarnings("unused")
    public static class ExecutableMirror extends AccessibleObjectMirror {
        private Object declaringClass;
        private Object declaringClassOfOverriddenMethod;
        private Object[] parameters;
        private long artMethod;
        private int accessFlags;
    }
    @SuppressWarnings("unused")
    public static class MethodHandleMirror {
        private MethodType type;
        private MethodType nominalType;
        private MethodHandleMirror cachedSpreadInvoker;
        private int handleKind;
        private long artFieldOrMethod;
    }
    @SuppressWarnings("unused")
    public static class ClassMirror {
        private ClassLoader classLoader;
        private Object componentType;
        private Object dexCache;
        private Object extData;
        private Object[] ifTable;
        private String name;
        private Object superClass;
        private Object vtable;
        private long iFields;
        private long methods;
        private long sFields;
        private int accessFlags;
        private int classFlags;
        private int classSize;
        private int clinitThreadId;
        private int dexClassDefIndex;
        private volatile int dexTypeIndex;
        private int numReferenceInstanceFields;
        private int numReferenceStaticFields;
        private int objectSize;
        private int objectSizeAllocFastPath;
        private int primitiveType;
        private int referenceInstanceOffsets;
        private int status;
        private short copiedMethodsOffset;
        private short virtualMethodsOffset;
    }
    static class NeverCall {
        private static void a() {}
        private static void b() {}
    }
    static class InvokeStub {
        public static Object invoke(Object... args) {
            throw new IllegalStateException("stub not replaced");
        }
    }

    private static long longFrom(Method m, Object target, Object... args) throws Exception {
        return (Long)m.invoke(target,args);
    }

    static void setExemptions(String... prefixes) throws Exception {
        Object unsafe=Class.forName("sun.misc.Unsafe").getDeclaredMethod("getUnsafe").invoke(null);
        Class<?> unsafeType=unsafe.getClass();
        Method offset=unsafeType.getMethod("objectFieldOffset", Field.class);
        Method getLong=unsafeType.getMethod("getLong",Object.class,long.class);
        Method putLong=unsafeType.getMethod("putLong",Object.class,long.class,long.class);
        Method getInt=unsafeType.getMethod("getInt",long.class);
        long artOff=longFrom(offset,unsafe,ExecutableMirror.class.getDeclaredField("artMethod"));
        long mhOff=longFrom(offset,unsafe,MethodHandleMirror.class.getDeclaredField("artFieldOrMethod"));
        long methodsOff=longFrom(offset,unsafe,ClassMirror.class.getDeclaredField("methods"));
        long realOff=longFrom(offset,unsafe,java.lang.reflect.Executable.class.getDeclaredField("artMethod"));
        if(artOff!=realOff)throw new IllegalStateException("incompatible ART Executable layout");

        Method a=NeverCall.class.getDeclaredMethod("a");
        Method b=NeverCall.class.getDeclaredMethod("b");
        a.setAccessible(true);b.setAccessible(true);
        MethodHandle ah=MethodHandles.lookup().unreflect(a);
        MethodHandle bh=MethodHandles.lookup().unreflect(b);
        long aa=longFrom(getLong,unsafe,ah,mhOff);
        long bb=longFrom(getLong,unsafe,bh,mhOff);
        long size=bb-aa;
        if(size<16 || size>256)throw new IllegalStateException("incompatible ART method size");
        long addr=longFrom(getLong,unsafe,NeverCall.class,methodsOff);
        long bias=aa-addr-size;

        Class<?> vm=Class.forName("dalvik.system.VMRuntime");
        long vmMethods=longFrom(getLong,unsafe,vm,methodsOff);
        int count=(Integer)getInt.invoke(unsafe,vmMethods);
        if(count<1 || count>10000)throw new IllegalStateException("bad ART method count");

        Method stub=InvokeStub.class.getDeclaredMethod("invoke",Object[].class);
        stub.setAccessible(true);
        long original=longFrom(getLong,unsafe,stub,artOff);
        Object runtime=null;
        boolean done=false;
        try {
            for(int i=0;i<count;i++){
                putLong.invoke(unsafe,stub,artOff,vmMethods+i*size+bias);
                String n=stub.getName();
                Class<?>[] args=stub.getParameterTypes();
                if("getRuntime".equals(n) && args.length==0 && runtime==null)runtime=stub.invoke(null);
                if("setHiddenApiExemptions".equals(n) && args.length==1 && args[0]==String[].class){
                    if(runtime==null)throw new IllegalStateException("VMRuntime method ordering changed");
                    stub.invoke(runtime,(Object)prefixes);
                    done=true;
                }
                if(runtime!=null && done)break;
            }
        } finally {
            putLong.invoke(unsafe,stub,artOff,original);
        }
        if(!done)throw new IllegalStateException("setHiddenApiExemptions not found");
    }
}
