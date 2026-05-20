package utils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


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
		Assert.assertEquals(Object.class, hierarchy.get(0));
		Assert.assertEquals(Mammal.class, hierarchy.get(1));
		Assert.assertEquals(Dog.class, hierarchy.get(2));
		Assert.assertEquals(3, hierarchy.size());
	}

	@Test
	public void testTraverseClassHierarchy_interfaceInputReturnsSelfOnly() {
		List<Class<?>> hierarchy = ReflectionUtils.traverseClassHierarchy(Pet.class);
		Assert.assertEquals(List.of(Pet.class), hierarchy);
	}

	@Test
	public void testTraverseClassHierarchy_returnsMutable() {
		List<Class<?>> hierarchy = ReflectionUtils.traverseClassHierarchy(Dog.class);
		hierarchy.add(Object.class);   // 변경 가능해야 함
		Assert.assertEquals(4, hierarchy.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testTraverseClassHierarchy_nullCls() {
		ReflectionUtils.traverseClassHierarchy(null);
	}

	// ----- getAllInterfaces -----

	@Test
	public void testGetAllInterfaces_concreteClass() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Dog.class);
		// Pet, Tagged, Animal (super-interface of Pet) 모두 포함되어야 함
		Assert.assertTrue(ifaces.contains(Pet.class));
		Assert.assertTrue(ifaces.contains(Tagged.class));
		Assert.assertTrue(ifaces.contains(Animal.class));
		Assert.assertFalse(ifaces.contains(Dog.class));
	}

	@Test
	public void testGetAllInterfaces_interfaceInputIncludesSelf() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Pet.class);
		Assert.assertEquals(Pet.class, ifaces.get(0));
		Assert.assertTrue(ifaces.contains(Animal.class));
	}

	@Test
	public void testGetAllInterfaces_returnsMutable() {
		List<Class<?>> ifaces = ReflectionUtils.getAllInterfaces(Dog.class);
		int prev = ifaces.size();
		ifaces.add(Serializable.class);
		Assert.assertEquals(prev + 1, ifaces.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetAllInterfaces_nullCls() {
		ReflectionUtils.getAllInterfaces(null);
	}

	// ----- getFieldList -----

	@Test
	public void testGetFieldList_includesInherited() {
		List<Field> fields = ReflectionUtils.getFieldList(Dog.class);
		boolean hasName = fields.stream().anyMatch(f -> f.getName().equals("name"));
		boolean hasLegs = fields.stream().anyMatch(f -> f.getName().equals("legs"));
		Assert.assertTrue("Dog 자체 필드", hasName);
		Assert.assertTrue("Mammal 부모 필드", hasLegs);
	}

	@Test
	public void testGetFieldList_returnsMutable() {
		List<Field> fields = ReflectionUtils.getFieldList(Dog.class);
		int prev = fields.size();
		fields.clear();
		Assert.assertEquals(0, fields.size());
		Assert.assertNotEquals(0, prev);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetFieldList_nullCls() {
		ReflectionUtils.getFieldList(null);
	}

	// ----- getFieldValue -----

	@Test
	public void testGetFieldValue_privateField() throws Exception {
		Dog dog = new Dog();
		String name = ReflectionUtils.getFieldValue(dog, "name");
		Assert.assertEquals("rex", name);
	}

	@Test
	public void testGetFieldValue_inheritedField() throws Exception {
		Dog dog = new Dog();
		int legs = ReflectionUtils.<Integer>getFieldValue(dog, "legs");
		Assert.assertEquals(4, legs);
	}

	@Test
	public void testGetFieldValue_staticField() throws Exception {
		Dog dog = new Dog();
		int count = ReflectionUtils.<Integer>getFieldValue(dog, "instanceCount");
		Assert.assertEquals(7, count);
	}

	@Test(expected = NoSuchFieldException.class)
	public void testGetFieldValue_missingField() throws Exception {
		Dog dog = new Dog();
		ReflectionUtils.getFieldValue(dog, "nonExistent");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetFieldValue_nullObj() throws Exception {
		ReflectionUtils.getFieldValue(null, "name");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetFieldValue_nullFieldName() throws Exception {
		ReflectionUtils.getFieldValue(new Dog(), null);
	}

	// ----- newInstance(Class) -----

	@Test
	public void testNewInstance_publicNoArg() throws Exception {
		Dog dog = ReflectionUtils.newInstance(Dog.class);
		Assert.assertNotNull(dog);
	}

	@Test
	public void testNewInstance_privateConstructor() throws Exception {
		WithPrivateCtor obj = ReflectionUtils.newInstance(WithPrivateCtor.class);
		Assert.assertEquals("private", obj.label);
	}

	@Test(expected = NoSuchMethodException.class)
	public void testNewInstance_noNoArgCtor() throws Exception {
		ReflectionUtils.newInstance(WithoutNoArgCtor.class);
	}

	@Test(expected = NoSuchMethodException.class)
	public void testNewInstance_interfaceInput() throws Exception {
		ReflectionUtils.newInstance(Pet.class);
	}

	@Test
	public void testNewInstance_abstractClassWrappedAsInternalException() throws Exception {
		try {
			ReflectionUtils.newInstance(AbstractClass.class);
			Assert.fail("InternalException 기대");
		}
		catch ( InternalException e ) {
			Assert.assertTrue(e.getCause() instanceof InstantiationException);
		}
	}

	@Test
	public void testNewInstance_throwingCtor() throws Exception {
		try {
			ReflectionUtils.newInstance(WithThrowingCtor.class);
			Assert.fail("InvocationTargetException 기대");
		}
		catch ( InvocationTargetException e ) {
			Assert.assertTrue(e.getTargetException() instanceof IllegalStateException);
			Assert.assertEquals("boom", e.getTargetException().getMessage());
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNewInstance_nullCls() throws Exception {
		ReflectionUtils.newInstance((Class<?>)null);
	}

	// ----- newInstance(String, Class) -----

	@Test
	public void testNewInstanceByName_compatible() throws Exception {
		Animal animal = ReflectionUtils.newInstance(Dog.class.getName(), Animal.class);
		Assert.assertTrue(animal instanceof Dog);
	}

	@Test(expected = ClassNotFoundException.class)
	public void testNewInstanceByName_classNotFound() throws Exception {
		ReflectionUtils.newInstance("no.such.Class$Missing", Object.class);
	}

	@Test
	public void testNewInstanceByName_incompatibleType() throws Exception {
		try {
			ReflectionUtils.newInstance(Dog.class.getName(), Runnable.class);
			Assert.fail("ClassCastException 기대");
		}
		catch ( ClassCastException e ) {
			Assert.assertTrue("'actual' 라벨 포함", e.getMessage().contains("actual="));
			Assert.assertTrue("'expected' 라벨 포함", e.getMessage().contains("expected="));
		}
	}

	@Test(expected = NoSuchMethodException.class)
	public void testNewInstanceByName_propagatesNoSuchMethod() throws Exception {
		ReflectionUtils.newInstance(WithoutNoArgCtor.class.getName(), Object.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNewInstanceByName_nullClsName() throws Exception {
		ReflectionUtils.newInstance(null, Object.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNewInstanceByName_nullTypeCls() throws Exception {
		ReflectionUtils.newInstance(Dog.class.getName(), null);
	}
}
