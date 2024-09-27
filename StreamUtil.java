package cn.yl.common.utils;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author YL
 * @Desc Streams工具类
 * @since 2024-09-11 22:06:23
 */
@SuppressWarnings("all")
public abstract class StreamUtil {

    /**
     * list过滤--转换
     */
    @SafeVarargs
    public static <T, R> List<R> map(Collection<T> list, Function<T, R> function, Predicate<T>... predicates) {
        Stream<T> stream = list.stream();
        for (Predicate<T> predicate : predicates) {
            stream = stream.filter(predicate);
        }
        return stream.map(function).collect(Collectors.toList());
    }

    /**
     * 指定字段去重
     *
     * @param list      需要去重的集合
     * @param functions 去重的字段
     * @return
     */
    @SafeVarargs
    public static <T, U extends Comparable<? super U>> List<T> distinct(Collection<T> list, Function<? super T, ? extends U>... functions) {
        for (Function<? super T, ? extends U> function : functions) {
            list = list.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(function))), ArrayList::new));
        }
        return (List<T>) list;
    }

    /**
     * 增强stream流 list遍历索引
     */
    public static <T> Consumer<T> consumerIndex(BiConsumer<Integer, T> consumer) {
        class Obj {
            int i;
        }
        Obj obj = new Obj();
        return t -> consumer.accept(obj.i++, t);
    }

    /**
     * 增强stream流 map遍历获取索引
     */
    public static <U, R> Function<U, R> functionIndex(BiFunction<Integer, U, R> function) {
        class Obj {
            int i;
        }
        Obj obj = new Obj();
        return t -> function.apply(obj.i++, t);
    }
}
