package io.pagenote.feature.pen;
import java.util.*;
// Supplied GraphUtils.isLine; unresolved JADX locals fixed using original smali.
// Only Point, angle helper and logging are substituted.
public class ReferenceLineOriginal {
 static final double ARROW_HEIGHT = 5.0;
 public static class Point { public double x,y; public Point(double x,double y){this.x=x;this.y=y;} }
 public static class ArrowInfo { public boolean arrow=false; public int arrowPosition=-1; }
 static double pointDistance(Point a,Point b){return Math.hypot(b.x-a.x,b.y-a.y);}
 static double angle(Point a,Point b,Point c){double x=a.x-b.x,y=a.y-b.y,u=c.x-b.x,v=c.y-b.y;return Math.toDegrees(Math.acos((x*u+y*v)/(Math.sqrt(x*x+y*y)*Math.sqrt(u*u+v*v))));}
    public static int isLine(List<Point> list, ArrowInfo arrowInfo) {
        int i;
        Point point;
        int i3;
        boolean z = true;
        boolean z2 = true;
        Point point2;
        Point point3;
        Point point4;
        Point point5;
        Point point6;
        int i4;
        int size = list.size();
        if (size <= 2) {
            return -1;
        }
        Point point7 = list.get(0);
        int i5 = size - 1;
        Point point8 = list.get(i5);
        float f = (float) point7.x;
        double d = (float) point7.y;
        double d3 = f;
        float fAtan2 = (float) Math.atan2(point8.y - d, point8.x - d3);
        if (Float.isNaN(fAtan2) || Float.isInfinite(fAtan2)) {
            fAtan2 = 0.0f;
        }
        double dPointDistance = pointDistance(point7, point8);
        float f3 = fAtan2;
        if (dPointDistance < 15.0f) {
            return -1;
        }
        int i6 = size / 7;
        if (i6 <= 3) {
            i6 = 3;
        } else if (i6 > 5) {
            i6 = 5;
        }
        
        if (size > 30) {
            i = 4;
        } else {
            i = size > 20 ? 3 : 2;
        }
        int i7 = i;
        int i8 = size / 2;
        int i9 = size > 20 ? 8 : 5;
        int i10 = i8;
        Point point9 = null;
        int i11 = i7;
        int i12 = 0;
        int i13 = 0;
        int i14 = 0;
        int i15 = 0;
        float f4 = 200.0f;
        int i16 = i6;
        double d4 = 0.0d;
        while (i11 < i5) {
            Point point10 = list.get(i11);
            if (point9 == null || pointDistance(point10, point9) >= 1.0d) {
                int i17 = i12;
                float fAtan3 = (float) Math.atan2(point10.y - d, point10.x - d3);
                if (Float.isNaN(fAtan3) || Float.isInfinite(fAtan3)) {
                    fAtan3 = (float) d4;
                }
                if (point9 != null) {
                    double dAtan2 = Math.atan2(point10.y - point9.y, point10.x - point9.x);
                    if (i11 > 8 && Math.abs(Math.abs(dAtan2) - Math.abs(d4)) > 1.0d) {
                        i13++;
                    }
                    if (i11 >= i9 && i11 < size - i9) {
                        float fAngleBetween2Lines = (float) angle(list.get(i11 - 4), list.get(i11 - 2), list.get(i11));
                        if (fAngleBetween2Lines < f4) {
                            f4 = fAngleBetween2Lines;
                            i10 = i11;
                        }
                    }
                    if (i11 <= 8 || Math.abs(Math.abs(dAtan2) - Math.abs(d4)) <= 1.0d) {
                        i4 = i17;
                    } else {
                        if (i17 == 0 && arrowInfo != null) {
                            arrowInfo.arrowPosition = i11;
                        }
                        i4 = i17 + 1;
                    }
                    i12 = i4;
                    d4 = dAtan2;
                } else {
                    i12 = i17;
                }
                if (Math.abs(Math.abs(f3) - Math.abs(fAtan3)) <= 0.3d || i13 > 1) {
                    i15 = i15;
                    i14 = i14;
                } else {
                    if (i13 == 0) {
                        i15++;
                    }
                    i14++;
                }
                point9 = point10;
            } else {
                d3 = d3;
            }
            i11++;
            i5 = i5;
            d = d;
            d3 = d3;
        }
        int i18 = i12;
        int i19 = i14;
        int i20 = i15;
        int i21 = arrowInfo.arrowPosition;
        if (i21 >= 0 && i21 < list.size()) {
            point = point8;
            double dPointDistance2 = pointDistance(point, list.get(arrowInfo.arrowPosition));
            i3 = 2;
            if (i18 >= 2 && dPointDistance2 > ARROW_HEIGHT) {
                z = true;
                arrowInfo.arrow = true;
            }
            z2 = (i13 >= i3 ? i19 >= i16 : i20 >= i16) ? false : z;
            if (z2 && !arrowInfo.arrow) {
                if (size > 10) {
                    int i22 = size / 4;
                    point4 = list.get(i22);
                    point5 = list.get(i10);
                    point6 = list.get(i22 * 3);
                    if (pointDistance(point7, point4) + pointDistance(point4, point5) + pointDistance(point5, point6) + pointDistance(point6, point) > dPointDistance * 1.03d) {
                        z2 = false;
                    }
                } else {
                    int i23 = size / 3;
                    point2 = list.get(i23);
                    point3 = list.get(i23 * 2);
                    if (pointDistance(point7, point2) + pointDistance(point2, point3) + pointDistance(point3, point) > dPointDistance * 1.05d) {
                        z2 = false;
                    }
                }
            }
            if (z2) {
                return 0;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("controlIndex: ");
            int i24 = i10;
            sb.append(i24);
            
            return i24 - 2;
        }
        point = point8;
        i3 = 2;
        z = true;
        z2 = i13 >= i3 ? i19 < i16 : i20 < i16;
        if (z2) {
            if (size > 10) {
                int i25 = size / 4;
                point4 = list.get(i25);
                point5 = list.get(i10);
                point6 = list.get(i25 * 3);
                if (pointDistance(point7, point4) + pointDistance(point4, point5) + pointDistance(point5, point6) + pointDistance(point6, point) > dPointDistance * 1.03d) {
                    z2 = false;
                }
            } else {
                int i26 = size / 3;
                point2 = list.get(i26);
                point3 = list.get(i26 * 2);
                if (pointDistance(point7, point2) + pointDistance(point2, point3) + pointDistance(point3, point) > dPointDistance * 1.05d) {
                    z2 = false;
                }
            }
        }
        if (z2) {
            return 0;
        }
        StringBuilder sb2 = new StringBuilder();
        sb2.append("controlIndex: ");
        int i27 = i10;
        sb2.append(i27);
        
        return i27 - 2;
    }
}