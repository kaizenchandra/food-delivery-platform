package dev.saffron;
import java.util.*;
public final class Workflow {
 public static String next(String current,String action){return switch(current+":"+action){
 case "PLACED:accept" -> "ACCEPTED"; case "ACCEPTED:prepare" -> "PREPARING";
 case "PREPARING:ready" -> "READY"; case "READY:assign" -> "ASSIGNED";
 case "ASSIGNED:pickup" -> "PICKED_UP"; case "PICKED_UP:deliver" -> "DELIVERED";
 case "ASSIGNED:release" -> "READY";
 case "AWAITING_PAYMENT:cancel","PLACED:cancel","ACCEPTED:cancel","PREPARING:cancel","READY:cancel" -> "CANCELLED";
 default -> throw new IllegalArgumentException("Invalid order transition");};}
 public static long quote(long price,int quantity){if(quantity<1||quantity>20)throw new IllegalArgumentException("Quantity must be 1–20");return Math.addExact(Math.multiplyExact(price,quantity),3900);}
}
