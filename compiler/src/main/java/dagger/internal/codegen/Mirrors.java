package dagger.internal.codegen;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.WILDCARD;

import java.util.Iterator;
import java.util.List;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.common.base.Equivalence;

public class Mirrors {
  private Mirrors() { }

  private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = new Equivalence<TypeMirror>() {
    @Override
    protected boolean doEquivalent(TypeMirror a, TypeMirror b) {
      return Mirrors.equal(a, b);
    }

    @Override
    protected int doHash(TypeMirror t) {
      return Mirrors.hash(t);
    }
  };

  static Equivalence<TypeMirror> equivalence() {
    return TYPE_EQUIVALENCE;
  }

  private static final TypeVisitor<Boolean, TypeMirror> EQUAL_VISITOR =
      new SimpleTypeVisitor6<Boolean, TypeMirror>() {
        protected Boolean defaultAction(TypeMirror a, TypeMirror b) {
          return a.getKind().equals(b.getKind());
        }

        @Override
        public Boolean visitArray(ArrayType a, TypeMirror m) {
          if (m.getKind().equals(ARRAY)) {
            ArrayType b = (ArrayType) m;
            return equal(a.getComponentType(), b.getComponentType());
          }
          return false;
        }

        @Override
        public Boolean visitDeclared(DeclaredType a, TypeMirror m) {
          if (m.getKind().equals(DECLARED)) {
            DeclaredType b = (DeclaredType) m;
            return a.asElement().equals(b.asElement())
                && a.getEnclosingType().accept(this, b.getEnclosingType())
                && equalLists(a.getTypeArguments(), b.getTypeArguments());

          }
          return false;
        }

        @Override
        public Boolean visitError(ErrorType a, TypeMirror m) {
          return a.equals(m);
        }

        @Override
        public Boolean visitExecutable(ExecutableType a, TypeMirror m) {
          if (m.getKind().equals(EXECUTABLE)) {
            ExecutableType b = (ExecutableType) m;
            return equalLists(a.getParameterTypes(), b.getParameterTypes())
                && equal(a.getReturnType(), b.getReturnType())
                && equalLists(a.getThrownTypes(), b.getThrownTypes())
                && equalLists(a.getThrownTypes(), b.getThrownTypes());
          }
          return false;
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable a, TypeMirror m) {
          if (m.getKind().equals(TYPEVAR)) {
            TypeVariable b = (TypeVariable) m;
            return equal(a.getUpperBound(), b.getUpperBound())
                && equal(a.getLowerBound(), b.getLowerBound());
          }
          return false;
        }

        @Override
        public Boolean visitWildcard(WildcardType a, TypeMirror m) {
          if (m.getKind().equals(WILDCARD)) {
            WildcardType b = (WildcardType) m;
            return equal(a.getExtendsBound(), b.getExtendsBound())
                && equal(a.getSuperBound(), b.getSuperBound());
          }
          return false;
        }

        public Boolean visitUnknown(TypeMirror a, TypeMirror p) {
          throw new UnsupportedOperationException();
        }
      };

  static boolean equal(TypeMirror a, TypeMirror b) {
    return (a == b) || (a != null && a.accept(EQUAL_VISITOR, b));
  }

  private static boolean equalLists(List<? extends TypeMirror> a, List<? extends TypeMirror> b) {
    int size = a.size();
    if (size != b.size()) {
      return false;
    }
    Iterator<? extends TypeMirror> aIterator = a.iterator();
    Iterator<? extends TypeMirror> bIterator = b.iterator();
    while (aIterator.hasNext()) {
      if (!bIterator.hasNext()) {
        return false;
      }
      TypeMirror nextMirrorA = aIterator.next();
      TypeMirror nextMirrorB = bIterator.next();
      if (!equal(nextMirrorA, nextMirrorB)) {
        return false;
      }
    }
    return !aIterator.hasNext();
  }

  private static final int HASH_SEED = 17;
  private static final int HASH_MULTIPLIER = 31;

  private static final TypeVisitor<Integer, Void> HASH_VISITOR =
      new SimpleTypeVisitor6<Integer, Void>() {
          int hashKind(int seed, TypeMirror t) {
            int result = seed * HASH_MULTIPLIER;
            result += t.getKind().hashCode();
            return result;
          }

          protected Integer defaultAction(TypeMirror e, Void p) {
            return hashKind(HASH_SEED, e);
          }

          @Override
          public Integer visitArray(ArrayType t, Void v) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getComponentType().accept(this, null);
            return result;
          }

          @Override
          public Integer visitDeclared(DeclaredType t, Void v) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.asElement().hashCode();
            result *= HASH_MULTIPLIER;
            result += t.getEnclosingType().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeArguments());
            return result;
          }

          @Override
          public Integer visitExecutable(ExecutableType t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getParameterTypes());
            result *= HASH_MULTIPLIER;
            result += t.getReturnType().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getThrownTypes());
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeVariables());
            return result;
          }

          @Override
          public Integer visitTypeVariable(TypeVariable t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getLowerBound().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += t.getUpperBound().accept(this, null);
            return result;
          }

          @Override
          public Integer visitWildcard(WildcardType t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getExtendsBound().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += t.getSuperBound().accept(this, null);
            return result;
          }

          public Integer visitUnknown(TypeMirror t, Void p) {
            throw new UnsupportedOperationException();
          }
      };

  static int hashList(List<? extends TypeMirror> mirrors) {
    int result = HASH_SEED;
    for (TypeMirror mirror : mirrors) {
      result *= HASH_MULTIPLIER;
      result += hash(mirror);
    }
    return result;
  }

  static int hash(TypeMirror mirror) {
    return mirror == null ? 0 : mirror.accept(HASH_VISITOR, null);
  }


}
