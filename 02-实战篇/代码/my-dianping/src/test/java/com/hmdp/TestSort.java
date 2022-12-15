package com.hmdp;

import com.hmdp.utils.PrintColor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;

public class TestSort {
    /**
     * 冒泡排序,相邻两个元素比较,按同一个的规则找到最大或者最小元素,最终将其移入最后.(冒泡)
     */
    @Test
    void testSwap() {

    }

    /**
     * 快速排序.
     */
    @Test
    void testPartition() {
        int[] array = {5, 3, 7, 2, 9, 8, 1, 4};
//        partition(array, 0, array.length - 1);
        quick(array, 0, array.length - 1);
    }

    void quick(int[] a, int start, int end) {
        if (start >= end) {
            return;
        }
        int index = partition(a, start, end);
        quick(a, start, index - 1);//左分区
        quick(a, index + 1, end);// 右分区
    }

    /**
     * @param a
     * @param start 数组起点
     * @param end   数组终点
     * @return 返回基准点元素所在的正确索引.用于确定下轮的正确边界.
     */
    int partition(int[] a, int start, int end) {
        int pv = a[end]; //基准点,默认最右边的元素
        int i = start;// 待交换元素
        for (int j = start; j < end; j++) {
            // 比较每个元素与基准点
            if (a[j] < pv) {
                xorSwap(a, i, j);
                System.out.println(Arrays.toString(a));
                i++;
            }
        }
        xorSwap(a, i, end);
        System.out.println(Arrays.toString(a) + " i ==> " + i);
        return i;
    }


    void swap(int[] a, int source, int destination) {
        int temp = a[source];
        a[source] = a[destination];
        a[destination] = temp;
    }

    @Test
    void testXorSwap() {
        Random random = new Random();
        int x = random.nextInt(100000);
        int y = random.nextInt(100000);
//        int x = 2;
//        int y = 2;
        System.out.println("x ==> " + x);
        System.out.println("y ==> " + y);
//        int temp = x ^ y;
//        x = temp ^ x;
//        y = temp ^ y;
        //优化
        x = x ^ y;
        y = x ^ y;
        x = x ^ y;
        System.out.println("x ==> " + x);
        System.out.println("y ==> " + y);
        int[] a = {2, 5, 2};
        xorSwap(a, 0, 2);
        System.out.println(Arrays.toString(a));
    }

    /**
     * 通过异或交换数组中的两个数.
     * t = x ^ y
     * x = t ^ x
     * y = t ^ y   // x ,y 互换
     * NOTICE: x和 y 不能是同一个数.
     *
     * @param a
     * @param source
     * @param destination
     */
    void xorSwap(int[] a, int source, int destination) {
        if (source == destination) {
            return;
        }
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        PrintColor.FG_BLUE.printWithColor("a[" + source + "] ==> " + a[source]);
        PrintColor.FG_BLUE.printWithColor("a[" + destination + "] ==> " + a[destination]);
        a[source] = a[source] ^ a[destination];
        a[destination] = a[source] ^ a[destination];
        a[source] = a[source] ^ a[destination];
        PrintColor.FG_BLUE.printWithColor("a[" + source + "] ==> " + a[source]);
        PrintColor.FG_BLUE.printWithColor("a[" + destination + "] ==> " + a[destination]);
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    }

    @Test
    public void test() {
        int[] a = {2, 3, 1};
        xorSwap(a, 1, 1);
    }
}
