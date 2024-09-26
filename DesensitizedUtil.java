package cn.yl.common.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * YL
 *
 * @author YL
 * @Desc 通用脱敏工具类  包括敏感词过滤
 * @since 2024-09-26 13:21:42
 */
@SuppressWarnings("all")
public abstract class DesensitizedUtil {

    public abstract static class Desensitize {
        private static final String DESENSITIZATION_TYPE = "desensitization.type";


        /**
         * 获取脱敏对象--默认--对象[字段脱敏字符替换]
         *
         * @param t
         * @param sensitiveFilter 敏感词过滤
         * @param <T>
         * @return
         */
        public static <T> T getDefaultDesensitizedObj(T t, boolean sensitiveFilter) {
            T o = createNewInstance(t); // 使用一个新方法创建实例
            try {
                processFields(t, o, 0, sensitiveFilter);
            } catch (IntrospectionException e) {
                return t;
            }
            return o;
        }

        /**
         * 获取脱敏对象--加密--对象[字段加密]
         *
         * @param t
         * @param sensitiveFilter 敏感词过滤
         * @param <T>
         * @return
         */
        public static <T> T getDesensitizedObj(T t, boolean sensitiveFilter) {
            T o = createNewInstance(t); // 使用一个新方法创建实例
            try {
                processFields(t, o, 1, sensitiveFilter);
            } catch (IntrospectionException e) {
                return t;
            }
            return o;
        }

        /**
         * 解密--对象[字段解密]
         *
         * @param t
         * @param sensitiveFilter 敏感词过滤
         * @param <T>
         * @return
         */
        public static <T> T getUnDesensitizedObj(T t, boolean sensitiveFilter) {
            T o = createNewInstance(t); // 使用一个新方法创建实例
            try {
                processFields(t, o, 2, sensitiveFilter);
            } catch (IntrospectionException e) {
                return t;
            }
            return o;
        }

        /**
         * 脱敏--对象[字段置空]
         *
         * @param t
         * @param sensitiveFilter 敏感词过滤
         * @param <T>
         * @return
         */
        public static <T> T getDesensitizedObjNull(T t, boolean sensitiveFilter) {
            T o = createNewInstance(t); // 使用一个新方法创建实例
            try {
                processFields(t, o, 3, sensitiveFilter);
            } catch (IntrospectionException e) {
                return t;
            }
            return o;
        }

        /**
         * 创建对象实例
         *
         * @param t
         * @param <T>
         * @return
         */
        private static <T> T createNewInstance(T t) {
            try {
                if (t.getClass().isArray()) {
                    Object[] arr = (Object[]) t;
                    Object[] os = new Object[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        os[i] = arr[i];
                    }
                    return (T) os;
                } else if (Collection.class.isAssignableFrom(t.getClass())) {
                    Collection c = (Collection) t;
                    Collection list = new ArrayList();
                    list.addAll(c);
                    return (T) list;
                } else if (Map.class.isAssignableFrom(t.getClass())) {
                    Map map = (Map) t;
                    Map newMap = new HashMap<>();
                    newMap.putAll(map);
                    return (T) newMap;
                } else {
                    return (T) t.getClass().getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                return t;
            }
        }

        /**
         * 处理字段
         *
         * @param source          源对象
         * @param target          目标对象
         * @param type            字段类型   需要解密的时候需要指明这个类型  2：解密
         * @param sensitiveFilter 敏感词过滤
         * @throws IntrospectionException
         */
        private static void processFields(Object source, Object target, int type, boolean sensitiveFilter) throws IntrospectionException {
            Class<?> sourceClass = source.getClass();
            BeanInfo beanInfo = Introspector.getBeanInfo(sourceClass);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String propertyName = propertyDescriptor.getName();
                Method readMethod = propertyDescriptor.getReadMethod();
                Method writeMethod = propertyDescriptor.getWriteMethod();
                Object value = null;
                Field field = null;
                try {
                    try {
                        field = sourceClass.getDeclaredField(propertyName);
                        value = readMethod.invoke(source);
                    } catch (Exception e) {
                        value = source;
                    }
                    //存放Object数组
                    Object[] newObj;
                    Collection newList;
                    //存放Object map
                    Map newMap;
                    if (source.getClass().isArray()) {
                        // 数组需要特别处理，因为不能直接调用方法
                        // ... 你可以使用Arrays.stream(array)或者循环遍历数组
                        // 如果是数组，并且你想将其转换为一个流
                        Object[] arrayobj = (Object[]) value;
                        newObj = new Object[arrayobj.length];
                        for (int i = 0; i < arrayobj.length; i++) {
                            if (!isSimpleType(arrayobj[i].getClass())) {
                                newObj[i] = switchType(arrayobj[i], type, sensitiveFilter); // 递归处理数组中的每个元素
                            }
                        }
                        // 假设我们知道setter方法的名称，并且它接受Object[]类型（或具体类型）
                        target = newObj;
                        return;
                    } else if (Collection.class.isAssignableFrom(source.getClass())) {
                        // 处理List
                        Collection list = (Collection) value;
                        newList = new ArrayList<>(list.size());
                        for (Object o : list) {
                            if (!isSimpleType(o.getClass())) {
                                newList.add(switchType(o, type, sensitiveFilter));
                            }
                        }
                        // 假设我们知道setter方法的名称，并且它接受List<Object>类型（或具体类型）
                        Collection targetList = (Collection) target;
                        targetList.clear();
                        targetList.addAll(newList);
                        return;
                    } else if (Map.class.isAssignableFrom(source.getClass())) {
                        // 处理Map
                        Map<Object, Object> map = (Map) value;
                        newMap = new HashMap<>(map.size());
                        for (Map.Entry<Object, Object> entry : map.entrySet()) {
                            if (!isSimpleType(entry.getValue().getClass())) {
                                entry.setValue(switchType(entry.getKey(), type, sensitiveFilter));
                            }
                        }
                        Map targetMap = (Map) target;
                        targetMap.putAll(newMap);
                        return;
                    }
                    field.setAccessible(true);
                    DesensitizedField annotation = field.getAnnotation(DesensitizedField.class);
                    if (!isSimpleType(field.getType())) {
                        if (type == 2) {
                            value = getUnDesensitizedObj(value, sensitiveFilter);
                        } else {
                            if ("0".equals(annotation.fieldControl())) {
                                value = getDefaultDesensitizedObj(value, sensitiveFilter);
                            } else if ("1".equals(annotation.fieldControl())) {
                                value = getDesensitizedObj(value, sensitiveFilter);
                            } else if ("2".equals(annotation.fieldControl())) {
                                value = getDesensitizedObjNull(value, sensitiveFilter);
                            }
                        }
                    }

                    if (annotation != null) {
                        Map<Integer, Integer> map = getType(annotation);
                        if (type == 2) {
                            //解密
                            value = undesensitizedField(value, map.get(0), map.get(1));
                        } else {
                            if ("0".equals(annotation.fieldControl())) {
                                value = desensitizedField(value, map.get(0), map.get(1), annotation.separator());
                            } else if ("1".equals(annotation.fieldControl())) {
                                //加密
                                value = desensitizedField(value, map.get(0), map.get(1));
                            } else if ("2".equals(annotation.fieldControl())) {
                                value = null;
                            }
                        }
                    }
                    if (value.getClass().isAssignableFrom(String.class) && sensitiveFilter) {
                        value = sensitiveFilter(String.valueOf(value));
                    }
                    writeMethod.invoke(target, value);
                } catch (Exception e) {

                }
            }
        }

        /**
         * 过滤敏感词 使用nio读取
         *
         * @param value
         * @return
         * @throws IOException
         */
        private static Object sensitiveFilter(String value) throws IOException {
            Resource resource = new ClassPathResource("sensitivewords.txt");
            BufferedReader bufferedReader = Files.newBufferedReader(resource.getFile().toPath());
            FileChannel channel = new FileInputStream(resource.getFile()).getChannel();
            ByteBuffer allocate = ByteBuffer.allocate(1024);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while (channel.read(allocate) != -1) {
                allocate.flip();
                CharBuffer charBuffer = Charset.defaultCharset().decode(allocate);
                String[] lines = charBuffer.toString().split("\n");
                for (String temp : lines) {
                    line = temp;
                    if (value.contains(temp)) {
                        for (int i = 0; i < temp.length(); i++) {
                            sb.append("*");
                        }
                    }
                }
                allocate.clear();
            }
            return value.replaceAll(line, sb.toString());
        }

        /**
         * 集合类型嵌套了非简单类型也需要递归
         *
         * @param value
         * @param type
         * @param sensitiveFilter 敏感词过滤
         * @return
         */
        private static Object switchType(Object value, int type, boolean sensitiveFilter) {
            switch (type) {
                case 0:
                    return getDefaultDesensitizedObj(value, sensitiveFilter);
                case 1:
                    return getDesensitizedObj(value, sensitiveFilter);
                case 2:
                    return getUnDesensitizedObj(value, sensitiveFilter);
                case 3:
                    return getDesensitizedObjNull(value, sensitiveFilter);
                default:
                    return value;
            }
        }

        /**
         * 设置脱敏参数
         *
         * @param desensitizedField
         * @return map
         * <div>
         *     <p>0-begin: 起始位置</p>
         *     <p>1-end  : 结束位置</p>
         *     <p>2-separator : 分隔符 若果是加密可以不设置</p>
         * </div>
         */
        private static Map<Integer, Integer> getType(DesensitizedField desensitizedField) {
            DesensitizedField.DesensitizedType type = desensitizedField.type();
            HashMap<Integer, Integer> map = new HashMap<>();
            switch (type) {
                // 自定义类型脱敏
                case MY_RULE:
                    int begin = desensitizedField.begin();
                    int end = desensitizedField.end();
                    map.put(0, begin);
                    map.put(1, end);
                    break;
                // 中文姓名脱敏
                case CHINESE_NAME:
                    map.put(0, 1);
                    map.put(1, 1);
                    break;
                // 身份证脱敏
                case ID_CARD:
                    map.put(0, 2);
                    map.put(1, 2);
                    break;
                // 手机号脱敏
                case MOBILE_PHONE:
                    map.put(0, 4);
                    map.put(1, 3);
                    break;
                // 地址脱敏
                case ADDRESS:

                    break;
                // 邮箱脱敏
                case EMAIL:

                    break;
                // 密码脱敏
                case PASSWORD:
                    map.put(0, 0);
                    map.put(1, 0);
                    break;
                // 中国车牌脱敏
                case CAR_LICENSE:

                    break;
                // 银行卡脱敏
                case BANK_CARD:

                    break;
                default:
            }
            return map;
        }


        /**
         * 辅助方法，用于设置字段值
         *
         * @param target
         * @param setterMethod
         * @param newValue
         * @throws Exception
         */
        private static void setFieldValue(Object target, Method writeMethod, Object newValue) throws Exception {
            writeMethod.invoke(target, newValue);
        }

        /**
         * 使用脱敏字符替换
         *
         * @param value
         * @param begin
         * @param end
         * @param separator
         * @return
         */
        private static Object desensitizedField(Object value, int begin, int end, String separator) {
            String s = String.valueOf(value);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= s.length(); i++) {
                if (i <= begin || s.length() - end < i) {
                    sb.append(s.charAt(i - 1));
                } else {
                    sb.append(separator);
                }
            }
            return sb.toString();
        }

        /**
         * 对应字段值加密
         *
         * @param value
         * @param begin
         * @param end
         * @return
         */
        private static Object desensitizedField(Object value, int begin, int end) {
            String s = String.valueOf(value);
            StringBuilder sb = new StringBuilder();
            String beginStr = s.substring(0, begin);
            String endStr = s.substring(s.length() - 1 - end);
            sb.append(beginStr);
            //需要加密字符串
            String encryStr = s.substring(begin, s.length() - end - 1);
            RSAUtil.AESCipher instance = RSAUtil.AESCipher.getInstance();
            //加密
            sb.append(instance.Encrypt(encryStr));
            sb.append(endStr);
            return sb.toString();
        }

        /**
         * 对应字段值解密
         *
         * @param value
         * @param begin
         * @param end
         * @return
         */
        private static Object undesensitizedField(Object value, int begin, int end) {
            String s = String.valueOf(value);
            StringBuilder sb = new StringBuilder();
            String beginStr = s.substring(0, begin);
            String endStr = s.substring(s.length() - 1 - end);
            sb.append(beginStr);
            //需要解密字符串
            String decryptStr = s.substring(begin, s.length() - end - 1);
            RSAUtil.AESCipher instance = RSAUtil.AESCipher.getInstance();
            //解密
            sb.append(instance.Decrypt(decryptStr));
            sb.append(endStr);
            return sb.toString();
        }

        /**
         * 判断是否为简单类型
         *
         * @param clazz
         * @return
         */
        private static boolean isSimpleType(Class<?> clazz) {
            // 这里可以添加更多的简单类型检查，比如基本类型、String、包装类型等
            return clazz.isPrimitive() || clazz.equals(String.class) || clazz.equals(Integer.class) || clazz.equals(Long.class) || clazz.equals(Double.class) || clazz.equals(Float.class) || clazz.equals(Boolean.class) || clazz.equals(Byte.class) || clazz.equals(Short.class) || clazz.equals(Character.class);
        }
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface DesensitizedField {
        /**
         * 脱敏数据类型，在MY_RULE的时候，必须有begin和end
         */
        DesensitizedType type() default DesensitizedType.MY_RULE;

        /**
         * 脱敏开始位置（包含）
         */
        int begin() default 0;

        /**
         * 脱敏结束位置（不包含）
         */
        int end() default 0;

        /**
         * 精准控制字段按照什么方式脱敏 只能填写数字类型的字符串
         * 0：默认脱敏分隔符  1：加密  2：将脱敏的字段置为空
         */
        String fieldControl() default "1";

        /**
         * 替换字符
         */
        String separator() default "*";

        enum DesensitizedType {
            //自定义
            MY_RULE,
            //用户id
            USER_ID,
            //中文名
            CHINESE_NAME,
            //身份证号
            ID_CARD,
            //座机号
            FIXED_PHONE,
            //手机号
            MOBILE_PHONE,
            //地址
            ADDRESS,
            //电子邮件
            EMAIL,
            //密码
            PASSWORD,
            //中国大陆车牌，包含普通车辆、新能源车辆
            CAR_LICENSE,
            //银行卡
            BANK_CARD
        }
    }
}
