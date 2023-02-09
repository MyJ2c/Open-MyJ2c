package cn.muyang;

public class NativeSignature {

 /*   public static void getJNIDefineName(String name, StringBuilder sb) {
        for (int cp : (Iterable<Integer>) name.chars()::iterator) {
            if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || cp == '_' || (cp >= '0' && cp <= '9')) {
                sb.append((char) cp);
            } else {
                sb.append('_');
            }
        }
    }*/

    public static void getJNICompatibleName(String name, StringBuilder sb) {
        for (int cp : (Iterable<Integer>) name.chars()::iterator) {
            if (cp < 127) {
                switch (cp) {
                    case '/':
                    case '.': {
                        sb.append('_');
                        break;
                    }
                    case '$': {
                        sb.append("_00024");
                        break;
                    }
                    case '_': {
                        sb.append("_1");
                        break;
                    }
                    case ';': {
                        sb.append("_2");
                        break;
                    }
                    case '[': {
                        sb.append("_3");
                        break;
                    }
                    default: {
                        sb.append((char) cp);
                        break;
                    }
                }
            } else {
                sb.append("_0");
                String hexed = Integer.toHexString(cp);
                for (int i = 0; i < 4 - hexed.length(); i++) {
                    sb.append('0');
                }
                sb.append(hexed);
            }
        }
    }

    public static String getJNICompatibleName(String name) {
        StringBuilder sb = new StringBuilder();
        getJNICompatibleName(name, sb);
        return sb.toString();
    }
}
