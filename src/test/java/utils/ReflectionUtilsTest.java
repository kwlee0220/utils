package utils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ReflectionUtilsTest {

	// ----- 테스트용 클래스/인터페이스 -----

	interface Animal {}
	interface Pet extends Animal {}
	interface Tagged {}

	static class Mammal implements Animal {
		@SuppressWarnings("unused")
		private int legs = 4;
	}

	static class Dog extends Mammal implements Pet, Tagged {
		@SuppressWarnings("unused")
		private String name = "rex";
		static int instanceCount = 7;
	}

	static class WithPrivateCtor {
		private final String label;
		private WithPrivateCtor() { this.label = "private"; }
	}

	static class WithThrowingCtor {
		WithThrowingCtor() {
			throw new IllegalStateException("boom");
		}
	}

	static class WithoutNoArgCtor {
		WithoutNoArgCtor(int x) {}
	}

	static abstract class AbstractClass {
		AbstractClass() {}
	}

	// ----- traverseClassHierarchy -----

	@Test
	public void testTraverseClassHierarchy_concreteClass() {
		List<Class<?>> hierarchy = ReflectionUtils.traverseClassHierarchy(Dog.class);
		Assertions.assertEquals(Object.class, hierarchy.get(0));
		Assertions.assertEquals(Mammal.class, hierarchy.get(1));
		Assertions.assertEquals(Dog.class, hierarchy.get(2));
		Assertions.assertEquals(3, hierarchy.size());
	}

	@Test
	public void testTraverseClassHierarchy_interfaceInputReturnsSelfOnly() {
		List<Class<?>> hierarchy = ReflectionUtils.traverseClassHierarchy(Pet.class);
		Assertions.assertEquals(List.of(Pet.class), hierarchy);
	}

	@Test
	public void testTraverseClassHierarchy_returnsMutable() {
		List<Class<?>> hierarchy = ReflectionUtils.traverseClassHierarchy(Dog.class);
		hierarchy.add(Object.class);   // 변경 가능해야 함
		Assertions.assertEquals(4, hierarchy.size());
	}

	@Test
	public void testTraverseClassHierarchy_nullCls() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.traverseClassHierarchy(null);
		});
	}

	// ----- getAllInterfaces -----

	@Test
	public void testGetAllInterfaces_concreteClass() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Dog.class);
		// Pet, Tagged, Animal (super-interface of Pet) 모두 포함되어야 함
		Assertions.assertTrue(ifaces.contains(Pet.class));
		Assertions.assertTrue(ifaces.contains(Tagged.class));
		Assertions.assertTrue(ifaces.contains(Animal.class));
		Assertions.assertFalse(ifaces.contains(Dog.class));
	}

	@Test
	public void testGetAllInterfaces_interfaceInputIncludesSelf() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Pet.class);
		Assertions.assertEquals(Pet.class, ifaces.get(0));
		Assertions.assertTrue(ifaces.contains(Animal.class));
	}

	@Test
	public void testGetAllInterfaces_returnsMutable() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Dog.class);
		int prev = ifaces.size();
		ifaces.add(Serializable.class);
		Assertions.assertEquals(prev + 1, ifaces.size());
	}

	@Test
	public void testGetAllInterfaces_nullCls() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.getAllInterfaces(null);
		});
	}

	// ----- getFieldList -----

	@Test
	public void testGetFieldList_includesInherited() {
		List<Field> fields = ReflectionUtils.getFieldList(Dog.class);
		boolean hasName = fields.stream().anyMatch(f -> f.getName().equals("name"));
		boolean hasLegs = fields.stream().anyMatch(f -> f.getName().equals("legs"));
		Assertions.assertTrue(hasName, "Dog 자체 필드");
		Assertions.assertTrue(hasLegs, "Mammal 부모 필드");
	}

	@Test
	public void testGetFieldList_returnsMutable() {
		List<Field> fields = ReflectionUtils.getFieldList(Dog.class);
		int prev = fields.size();
		fields.clear();
		Assertions.assertEquals(0, fields.size());
		Assertions.assertNotEquals(0, prev);
	}

	@Test
	public void testGetFieldList_nullCls() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.getFieldList(null);
		});
	}

	// ----- getFieldValue -----

	@Test
	public void testGetFieldValue_privateField() throws Exception {
		Dog dog = new Dog();
		String name = ReflectionUtils.getFieldValue(dog, "name");
		Assertions.assertEquals("rex", name);
	}

	@Test
	public void testGetFieldValue_inheritedField() throws Exception {
		Dog dog = new Dog();
		int legs = ReflectionUtils.<Integer>getFieldValue(dog, "legs");
		Assertions.assertEquals(4, legs);
	}

	@Test
	public void testGetFieldValue_staticField() throws Exception {
		Dog dog = new Dog();
		int count = ReflectionUtils.<Integer>getFieldValue(dog, "instanceCount");
		Assertions.assertEquals(7, count);
	}

	@Test
	public void testGetFieldValue_missingField() throws Exception {
		Assertions.assertThrows(NoSuchFieldException.class, () -> {
			Dog dog = new Dog();
			ReflectionUtils.getFieldValue(dog, "nonExistent");
		});
	}

	@Test
	public void testGetFieldValue_nullObj() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.getFieldValue(null, "name");
		});
	}

	@Test
	public void testGetFieldValue_nullFieldName() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.getFieldValue(new Dog(), null);
		});
	}

	// ----- newInstance(Class) -----

	@Test
	public void testNewInstance_publicNoArg() throws Exception {
		Dog dog = ReflectionUtils.newInstance(Dog.class);
		Assertions.assertNotNull(dog);
	}

	@Test
	public void testNewInstance_privateConstructor() throws Exception {
		WithPrivateCtor obj = ReflectionUtils.newInstance(WithPrivateCtor.class);
		Assertions.assertEquals("private", obj.label);
	}

	@Test
	public void testNewInstance_noNoArgCtor() throws Exception {
		Assertions.assertThrows(NoSuchMethodException.class, () -> {
			ReflectionUtils.newInstance(WithoutNoArgCtor.class);
		});
	}

	@Test
	public void testNewInstance_interfaceInput() throws Exception {
		Assertions.assertThrows(NoSuchMethodException.class, () -> {
			ReflectionUtils.newInstance(Pet.class);
		});
	}

	@Test
	public void testNewInstance_abstractClassWrappedAsInternalException() throws Exception {
		try {
			ReflectionUtils.newInstance(AbstractClass.class);
			Assertions.fail("InternalException 기대");
		}
		catch ( InternalException e ) {
			Assertions.assertTrue(e.getCause() instanceof InstantiationException);
		}
	}

	@Test
	public void testNewInstance_throwingCtor() throws Exception {
		try {
			ReflectionUtils.newInstance(WithThrowingCtor.class);
			Assertions.fail("InvocationTargetException 기대");
		}
		catch ( InvocationTargetException e ) {
			Assertions.assertTrue(e.getTargetException() instanceof IllegalStateException);
			Assertions.assertEquals("boom", e.getTargetException().getMessage());
		}
	}

	@Test
	public void testNewInstance_nullCls() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.newInstance((Class<?>)null);
		});
	}

	// ----- newInstance(String, Class) -----

	@Test
	public void testNewInstanceByName_compatible() throws Exception {
		Animal animal = ReflectionUtils.newInstance(Dog.class.getName(), Animal.class);
		Assertions.assertTrue(animal instanceof Dog);
	}

	@Test
	public void testNewInstanceByName_classNotFound() throws Exception {
		Assertions.assertThrows(ClassNotFoundException.class, () -> {
			ReflectionUtils.newInstance("no.such.Class$Missing", Object.class);
		});
	}

	@Test
	public void testNewInstanceByName_incompatibleType() throws Exception {
		try {
			ReflectionUtils.newInstance(Dog.class.getName(), Runnable.class);
			Assertions.fail("ClassCastException 기대");
		}
		catch ( ClassCastException e ) {
			Assertions.assertTrue(e.getMessage().contains("actual="), "'actual' 라벨 포함");
			Assertions.assertTrue(e.getMessage().contains("expected="), "'expected' 라벨 포함");
		}
	}

	@Test
	public void testNewInstanceByName_propagatesNoSuchMethod() throws Exception {
		Assertions.assertThrows(NoSuchMethodException.class, () -> {
			ReflectionUtils.newInstance(WithoutNoArgCtor.class.getName(), Object.class);
		});
	}

	@Test
	public void testNewInstanceByName_nullClsName() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.newInstance(null, Object.class);
		});
	}

	@Test
	public void testNewInstanceByName_nullTypeCls() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.newInstance(Dog.class.getName(), null);
		});
	}
}
