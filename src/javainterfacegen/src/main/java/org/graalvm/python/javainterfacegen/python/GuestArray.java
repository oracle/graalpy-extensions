/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.python.javainterfacegen.python;

import java.util.AbstractList;


public class GuestArray<T> extends AbstractList<T> implements GuestValue {

    private final org.graalvm.polyglot.Value instance;
    private final java.util.List<org.graalvm.polyglot.Value> instanceList;
    private final java.util.function.Function<org.graalvm.polyglot.Value, T> mapper;

    public GuestArray(org.graalvm.polyglot.Value instance, java.util.function.Function<org.graalvm.polyglot.Value, T> mapper) {
        this.instance = instance;
        this.mapper = mapper;
        this.instanceList = instance.as(java.util.List.class);
    }

    @Override
    public org.graalvm.polyglot.Value getValue() {
        return this.instance;
    }

    @Override
    public int size() {
        return (int)this.instance.getArraySize();
    }

    @Override
    public boolean isEmpty() {
        return this.instance.getArraySize() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return this.instanceList.contains(o instanceof GuestValue ? ((GuestValue) o).getValue(): o);
    }

    @Override
    public java.util.Iterator<T> iterator() {
        return this.instanceList.stream().map(this.mapper).iterator();
    }

    @Override
    public Object[] toArray() {
        return this.instanceList.stream().map(this.mapper).toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return java.util.Arrays.asList(this.instanceList.stream().map(this.mapper)).toArray(a);
    }

    @Override
    public boolean add(T t) {
        return this.instanceList.add(t instanceof GuestValue ? ((GuestValue) t).getValue() : org.graalvm.polyglot.Value.asValue(t));
    }

    @Override
    public boolean remove(Object o) {
        return this.instanceList.remove(o instanceof GuestValue ? ((GuestValue) o).getValue() : o);
    }

//    @Override
//    public void replaceAll(java.util.function.UnaryOperator<T> operator) {
//        java.util.List.super.replaceAll(operator);
//    }
//
//    @Override
//    public void sort(java.util.Comparator<? super T> c) {
//        java.util.List.super.sort(c);
//    }
//
//    @Override
//    public java.util.Spliterator<T> spliterator() {
//        return java.util.List.super.spliterator();
//    }

    @Override
    public boolean containsAll(java.util.Collection<?> c) {
        return this.instanceList.containsAll(c.stream().map(o -> o instanceof GuestValue ? ((GuestValue) o).getValue() : o).collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public boolean addAll(java.util.Collection<? extends T> c) {
        return this.instanceList.addAll(c.stream().map(o -> o instanceof GuestValue ? ((GuestValue) o).getValue() : org.graalvm.polyglot.Value.asValue(o)).collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public boolean addAll(int index, java.util.Collection<? extends T> c) {
        return this.instanceList.addAll(index, c.stream().map(o -> o instanceof GuestValue ? ((GuestValue) o).getValue() : org.graalvm.polyglot.Value.asValue(o)).collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public boolean removeAll(java.util.Collection<?> c) {
        return this.instanceList.removeAll(c.stream().map(o -> o instanceof GuestValue ? ((GuestValue) o).getValue() : o).collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public boolean retainAll(java.util.Collection<?> c) {
        return this.instanceList.retainAll(c.stream().map(o -> o instanceof GuestValue ? ((GuestValue) o).getValue() : o).collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public void clear() {
        this.instanceList.clear();
    }

    @Override
    public T get(int index) {
        return this.mapper.apply(this.instance.getArrayElement(index));
    }

    @Override
    public T set(int index, T element) {
        return this.mapper.apply(org.graalvm.polyglot.Value.asValue(this.instanceList.set(index, element instanceof GuestValue ? ((GuestValue) element).getValue() : org.graalvm.polyglot.Value.asValue(element))));
    }

    @Override
    public void add(int index, T element) {
        this.instanceList.add(index, element instanceof GuestValue ? ((GuestValue) element).getValue() : org.graalvm.polyglot.Value.asValue(element));
    }

    @Override
    public T remove(int index) {
        return this.mapper.apply(org.graalvm.polyglot.Value.asValue(this.instanceList.remove(index)));
    }

    @Override
    public int indexOf(Object o) {
        return this.instanceList.indexOf(o instanceof GuestValue ? ((GuestValue) o).getValue() : o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.instanceList.lastIndexOf(o instanceof GuestValue ? ((GuestValue) o).getValue() : o);
    }

    @Override
    public java.util.ListIterator<T> listIterator() {
        return this.instanceList.stream().map(this.mapper).collect(java.util.stream.Collectors.toList()).listIterator();
    }

    @Override
    public java.util.ListIterator<T> listIterator(int index) {
        return this.instanceList.stream().map(this.mapper).collect(java.util.stream.Collectors.toList()).listIterator(index);
    }

    @Override
    public java.util.List<T> subList(int fromIndex, int toIndex) {
        return this.instanceList.stream().skip(fromIndex).limit(toIndex - fromIndex).map(this.mapper).collect(java.util.stream.Collectors.toList());
    }
}
