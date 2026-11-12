package io.pagenote.feature.pen;

// Test oracle transcribed from the supplied GraphUtils.isBrokenLine. The
// Android/OpenCV Point and PanelManager dependencies are replaced with simple
// coordinates and an explicit toScreenWidth(15) value; logging is omitted.
import java.util.*;
public class ReferenceBrokenLineOriginal {
 public static class Point { public double x,y; public Point(double x,double y){this.x=x;this.y=y;} }
 static double pointDistance(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
 static double angle(Point a,Point b,Point c){double x=a.x-b.x,y=a.y-b.y,u=c.x-b.x,v=c.y-b.y;return Math.toDegrees(Math.acos((x*u+y*v)/(Math.sqrt(x*x+y*y)*Math.sqrt(u*u+v*v))));}
    public static List<Integer> find(List<Point> list, float threshold) {
        int i;
        int i3;
        int i4;
        boolean z;
        boolean z2;
        ArrayList arrayList = new ArrayList();
        int size = list.size();
        if (size <= 2) {
            return arrayList;
        }
        Point point = list.get(0);
        int i5 = size - 1;
        Point point2 = list.get(i5);
        float f = (float) point.x;
        double d = (float) point.y;
        double d3 = f;
        float fAtan2 = (float) Math.atan2(point2.y - d, point2.x - d3);
        if (!Float.isNaN(fAtan2)) {
            Float.isInfinite(fAtan2);
        }
        float screenWidth = threshold;
        double d4 = screenWidth;
        if (pointDistance(point, point2) < d4) {
            return arrayList;
        }
        if (size > 30) {
            i = 4;
        } else {
            i = size > 20 ? 3 : 2;
        }
        Point point3 = list.get(0);
        int i6 = 3;
        float f3 = 200.0f;
        Point point4 = null;
        int i7 = 0;
        int i8 = 0;
        boolean z3 = true;
        int i9 = 0;
        int i10 = 0;
        int i11 = 0;
        boolean z4 = false;
        while (i < i5) {
            Point point5 = list.get(i);
            if (point4 == null || pointDistance(point5, point4) >= 1.0d) {
                float fAtan3 = (float) Math.atan2(point5.y - d, point5.x - d3);
                if (!Float.isNaN(fAtan3)) {
                    Float.isInfinite(fAtan3);
                }
                if (point4 != null) {
                    Math.atan2(point5.y - point4.y, point5.x - point4.x);
                    if (i < 5 || i >= i5) {
                        point3 = point3;
                        i3 = i9;
                    } else {
                        float fAngleBetween2Lines = (float) angle(list.get(i - 3), list.get(i - 1), list.get(i));
                        if (fAngleBetween2Lines < 150.0f) {
                            z4 = true;
                        }
                        if (Math.abs(fAngleBetween2Lines - 180.0f) >= 8.0f || Float.isNaN(fAngleBetween2Lines)) {
                            if (i8 >= 1) {
                                i4 = 0;
                                z = false;
                            } else {
                                i4 = i8 + 1;
                                z = z3;
                            }
                            if (fAngleBetween2Lines < f3) {
                                i11 = i;
                                f3 = fAngleBetween2Lines;
                            }
                            i8 = i4;
                            z2 = z;
                            i3 = i9;
                        } else {
                            int i12 = i9 + 1;
                            int i13 = i8 - 1;
                            if (i13 < 0) {
                                i13 = 0;
                            }
                            i10 = i;
                            i8 = i13;
                            f3 = 200.0f;
                            i3 = i12;
                            z2 = true;
                        }
                        if (z3 || !z2) {
                            z3 = z2;
                            point3 = point3;
                        } else {
                            Point point6 = point3;
                            float fPointDistance = (float) pointDistance(point6, list.get(i));
                            if (i3 >= 4) {
                                int i14 = i10 - 3;
                                angle(point6, list.get((i14 + i6) / 2), list.get(i14));
                                if (fPointDistance > screenWidth) {
                                    arrayList.add(Integer.valueOf(i11));
                                    f3 = 200.0f;
                                }
                            } else if (fPointDistance > screenWidth && i < i5) {
                                i7++;
                            }
                            point3 = list.get(i);
                            i6 = i;
                            z3 = z2;
                            i3 = 0;
                        }
                    }
                    i9 = i3;
                }
                point4 = point5;
            } else {
                d3 = d3;
            }
            i++;
            d4 = d4;
            d = d;
            d3 = d3;
        }
        double d5 = d4;
        if ((arrayList.size() <= 2 && i7 >= 1) || arrayList.size() / 2 < i7 || arrayList.size() >= 5) {
            arrayList.clear();
        }
        if (arrayList.size() == 1 && ((float) angle(list.get(0), list.get(((Integer) arrayList.get(0)).intValue()), list.get(list.size() - 1))) > 170.0f) {
            arrayList.clear();
        }
        if (arrayList.size() > 0 && pointDistance(list.get(((Integer) arrayList.get(arrayList.size() - 1)).intValue()), point2) < d5) {
            arrayList.remove(arrayList.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## BIG: ");
        boolean z5 = z4;
        sb.append(z5);
        
        if (!z5) {
            arrayList.clear();
        }
        return arrayList;
    }

}
