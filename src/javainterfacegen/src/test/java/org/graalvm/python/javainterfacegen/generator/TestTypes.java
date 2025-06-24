package org.graalvm.python.javainterfacegen.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeAlias;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.LiteralType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.ProperType;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarId;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarLikeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVisitor;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;

public class TestTypes {

    public static class FakeType implements Type {

        @Override
        public boolean canBeTrue() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public boolean canBeFalse() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public boolean canBeTrueDefault() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public boolean canBeFalseDefault() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Value getValue() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

    }

    public static class FakeProperType extends FakeType implements ProperType {

    }

    public static class FakeInstance extends FakeProperType implements Instance {

        private final TestNodes.FakeTypeInfo typeInfo;
        private final List<Type> args;

        public FakeInstance(String fullname) {
            this.typeInfo = new TestNodes.FakeTypeInfo(fullname);
            this.args = new ArrayList();
        }
        
        public FakeInstance(String fullname, List<Type> args) {
            this.typeInfo = new TestNodes.FakeTypeInfo(fullname);
            this.args = args;
        }

        @Override
        public TypeInfo getType() {
            return typeInfo;
        }

        public void addArg(Type type) {
            this.args.add(type);
        }

        @Override
        public List<Type> getArgs() {
            return this.args;
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(typeInfo.getFullname());
            if (!args.isEmpty()) {
                sb.append("[");
                boolean first = true;
                for (Type type : args) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    sb.append(type.toString());
                }
                sb.append("]");
            }
            return sb.toString();
            
        }
        
    }

    public static class FakeUnion extends FakeProperType implements UnionType {

        private final List<Type> items;

        public FakeUnion(Type... types) {
            items = new ArrayList<>(Arrays.asList(types));
        }

        @Override
        public List<Type> getItems() {
            return items;
        }

        @Override
        public boolean isEvaluated() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Union[");
            boolean first = true;
            for(Type item: items) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(item.toString());
            }
            sb.append("]");
            return sb.toString();
        }

    }

    public static class FakeNone extends FakeType implements NoneType {

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            return "None";
        }
    }

    public static class FakeAny extends FakeProperType implements AnyType {

        @Override
        public int getTypeOfAny() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public boolean isFromError() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Optional<AnyType> getSourceAny() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Optional<String> getMissingImportName() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            return "Any";
        }
    }

    public static class FakeTypeAliasType extends FakeType implements TypeAliasType {

        private final TypeAlias typeAliasNode;

        public FakeTypeAliasType(TypeAlias typeAliasNode) {
            this.typeAliasNode = typeAliasNode;
        }

        @Override
        public TypeAlias getAlias() {
            return this.typeAliasNode;
        }

        @Override
        public String getTypeRef() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
    
    public static class FakeTypeVarLikeType extends FakeProperType implements TypeVarLikeType {

        private final String name;

        public FakeTypeVarLikeType(String name) {
            this.name = name;
        }

        @Override
        public boolean hasDefault() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Type getDefaultValue() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFullname() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public TypeVarId getId() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Type getUpperBound() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
        
    }
    
    public static class FakeTypeVarType extends FakeTypeVarLikeType implements TypeVarType {

        public FakeTypeVarType(String name) {
            super(name);
        }

        @Override
        public int getVariance() {
            return 0;
        }

        @Override
        public List<Type> getValues() {
            return Collections.EMPTY_LIST;
        }
        
    }
    
    public static class FakeLiteralType extends FakeProperType implements LiteralType {

        private final Object value;
        private final Instance instance;

        public FakeLiteralType(Object value, Instance instance) {
            this.value = value;
            this.instance = instance;
        }
        
        
        @Override
        public String valueRepr() {
            return value.toString();
        }

        @Override
        public boolean isSingletonType() {
            return false;
        }

        @Override
        public boolean isEnumLiteral() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Value getValueOf() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public Instance getFallback() {
            return instance;
        }

        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
    
    public static class FakeTupleType extends FakeProperType implements TupleType {
        private final List<Type> items;
        private final Instance fallback;

        public FakeTupleType(List<Type> items, Instance fallback) {
            this.items = items;
            this.fallback = fallback;
        }
        
        
        @Override
        public boolean canBeAnyBool() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean implicit() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int length() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Instance getPartialFallback() {
            return fallback;
        }

        @Override
        public List<Type> getItems() {
            return items;
        }
        
        @Override
        public <T> T accept(TypeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
    
    
}
