package com.extendedae_plus.util;

import java.util.Arrays;

public class ArraySimplifier {

    // 计算两个数的GCD using Euclidean algorithm (long版本)
    public static long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    // 计算整个数组的GCD
    public static long findGCD(long[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        long result = arr[0];
        for (int i = 1; i < arr.length; i++) {
            result = gcd(result, arr[i]);
            // 如果已经找到GCD为1，可以提前终止
            if (result == 1) {
                break;
            }
        }
        return result;
    }

    // 简化数组：每个元素除以数组的GCD
    public static long[] simplifyFraction(long[] arr) {
        if (arr.length == 0) {
            return new long[0];
        }
        long gcd = findGCD(arr);
        if (gcd == 0) {
            // 如果GCD为0（所有元素为0），返回原数组的副本
            return Arrays.copyOf(arr, arr.length);
        }
        long[] simplified = new long[arr.length];
        for (int i = 0; i < arr.length; i++) {
            simplified[i] = arr[i] / gcd;
        }
        return simplified;
    }

    // 将两个数组合并为一个新数组（先放 a 后放 b）
    public static long[] combine(long[] a, long[] b) {
        long[] out = new long[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // 寻找数组的 GCD，遇到 1 则立即返回 1（早期退出优化）
    public static long findGCDWithEarlyExit(long[] arr) {
        if (arr.length == 0) return 0;
        long result = 0;
        for (long v : arr) {
            if (v == 1) return 1; // already irreducible
            if (v == 0) continue;
            if (result == 0) result = v; else result = gcd(result, v);
            if (result == 1) return 1;
        }
        return result == 0 ? 0 : Math.abs(result);
    }

    // 根据给定的 gcd 返回一个已除以 gcd 的新数组；如果 gcd==1 返回原数组（避免不必要的分配）
    public static long[] simplifyByGcd(long[] arr, long gcd) {
        if (gcd <= 1) return arr;
        long[] out = new long[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i] / gcd;
        return out;
    }
}