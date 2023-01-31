/**
 * Copyright (c) 2012 - 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.ods.tests.helper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.text.RandomStringGenerator;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.gecko.emf.osgi.example.model.basic.Address;
import org.gecko.emf.osgi.example.model.basic.BasicFactory;
import org.gecko.emf.osgi.example.model.basic.BasicPackage;
import org.gecko.emf.osgi.example.model.basic.BusinessPerson;
import org.gecko.emf.osgi.example.model.basic.ContactContextType;
import org.gecko.emf.osgi.example.model.basic.ContactType;
import org.gecko.emf.osgi.example.model.basic.EmployeeInfo;
import org.gecko.emf.osgi.example.model.basic.Family;
import org.gecko.emf.osgi.example.model.basic.GenderType;
import org.gecko.emf.osgi.example.model.basic.Person;
import org.gecko.emf.osgi.example.model.basic.PersonContact;
import org.gecko.emf.osgi.example.model.basic.Tag;
import org.gecko.emf.osgi.example.model.basic.util.BasicResourceFactoryImpl;

public class EMFODSExporterTestHelper {

	public static Family createSimpsonFamily(BasicFactory bf) {
		Family simpsonFamily = bf.createFamily();
		simpsonFamily.setId("Simpsons");

		Address address = createSimpsonsAddress(bf);

		Person homerSimpson = createHomerSimpson(bf, address);
		simpsonFamily.setFather(homerSimpson);

		Person margeSimpson = createMargeSimpson(bf, address);
		simpsonFamily.setMother(margeSimpson);

		Person bartSimpson = createBartSimpson(bf, address);
		simpsonFamily.getChildren().add(bartSimpson);

		Person lisaSimpson = createLisaSimpson(bf, address);
		simpsonFamily.getChildren().add(lisaSimpson);

		Person maggieSimpson = createMaggieSimpson(bf, address);
		simpsonFamily.getChildren().add(maggieSimpson);

		homerSimpson.getRelatives().add(margeSimpson);
		homerSimpson.getRelatives().add(bartSimpson);
		homerSimpson.getRelatives().add(lisaSimpson);
		homerSimpson.getRelatives().add(maggieSimpson);

		margeSimpson.getRelatives().add(homerSimpson);
		margeSimpson.getRelatives().add(bartSimpson);
		margeSimpson.getRelatives().add(lisaSimpson);
		margeSimpson.getRelatives().add(maggieSimpson);

		bartSimpson.getRelatives().add(homerSimpson);
		bartSimpson.getRelatives().add(margeSimpson);
		bartSimpson.getRelatives().add(lisaSimpson);
		bartSimpson.getRelatives().add(maggieSimpson);

		lisaSimpson.getRelatives().add(homerSimpson);
		lisaSimpson.getRelatives().add(margeSimpson);
		lisaSimpson.getRelatives().add(bartSimpson);
		lisaSimpson.getRelatives().add(maggieSimpson);

		maggieSimpson.getRelatives().add(homerSimpson);
		maggieSimpson.getRelatives().add(margeSimpson);
		maggieSimpson.getRelatives().add(lisaSimpson);
		maggieSimpson.getRelatives().add(maggieSimpson);

		homerSimpson.getTags().add(createMultiLevelTag(bf, createUniquePrefix(10)));

		homerSimpson.setBigInt(BigInteger.TEN);

		homerSimpson.getBigDec().add(BigDecimal.ZERO);
		homerSimpson.getBigDec().add(BigDecimal.ONE);
		homerSimpson.getBigDec().add(BigDecimal.TEN);

		homerSimpson.setImage(createByteArr());

		homerSimpson.getProperties().putAll(createProperties(createUniquePrefix(10)));

		return simpsonFamily;
	}

	private static Address createSimpsonsAddress(BasicFactory bf) {
		return createAddress(bf, "742 Evergreen Terrace", "Springfield", "97482");
	}

	private static Person createHomerSimpson(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Homer", "Simpson", GenderType.MALE, address);

		p.getContact().add(createHomePhonePersonContact(bf, p));
		p.getContact().add(createHomeMobilePersonContact(bf, p));
		p.getContact().add(createHomeWhatsAppPersonContact(bf, p));
		p.getContact().add(createHomeEmailPersonContact(bf, p));
		p.getContact().add(createHomeSkypePersonContact(bf, p));
		p.getContact().add(createHomeWebAddressPersonContact(bf, p));

		return p;
	}

	private static Person createMargeSimpson(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Marge", "Simpson", GenderType.FEMALE, address);

		p.getContact().add(createHomePhonePersonContact(bf, p));
		p.getContact().add(createHomeMobilePersonContact(bf, p));
		p.getContact().add(createHomeWhatsAppPersonContact(bf, p));
		p.getContact().add(createHomeEmailPersonContact(bf, p));
		p.getContact().add(createHomeSkypePersonContact(bf, p));
		p.getContact().add(createHomeWebAddressPersonContact(bf, p));

		return p;
	}

	private static Person createBartSimpson(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Bart", "Simpson", GenderType.MALE, address);

		return p;
	}

	private static Person createLisaSimpson(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Lisa", "Simpson", GenderType.FEMALE, address);

		return p;
	}

	private static Person createMaggieSimpson(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Maggie", "Simpson", GenderType.FEMALE, address);

		return p;
	}

	public static Family createFlintstonesFamily(BasicFactory bf) {
		Family flintstonesFamily = bf.createFamily();
		flintstonesFamily.setId("Flintstones");

		Address address = createFlintstonesAddress(bf);

		Person fredFlintstone = createFredFlintstone(bf, address);
		flintstonesFamily.setFather(fredFlintstone);

		Person wilmaFlintstone = createWilmaFlintstone(bf, address);
		flintstonesFamily.setMother(wilmaFlintstone);

		Person pebblesFlintstone = createPebblesFlintstone(bf, address);
		flintstonesFamily.getChildren().add(pebblesFlintstone);

		Person stonyFlintstone = createStonyFlintstone(bf, address);
		flintstonesFamily.getChildren().add(stonyFlintstone);

		fredFlintstone.getRelatives().add(wilmaFlintstone);
		fredFlintstone.getRelatives().add(pebblesFlintstone);
		fredFlintstone.getRelatives().add(stonyFlintstone);

		wilmaFlintstone.getRelatives().add(fredFlintstone);
		wilmaFlintstone.getRelatives().add(pebblesFlintstone);
		wilmaFlintstone.getRelatives().add(stonyFlintstone);

		pebblesFlintstone.getRelatives().add(fredFlintstone);
		pebblesFlintstone.getRelatives().add(wilmaFlintstone);
		pebblesFlintstone.getRelatives().add(stonyFlintstone);

		stonyFlintstone.getRelatives().add(fredFlintstone);
		stonyFlintstone.getRelatives().add(wilmaFlintstone);
		stonyFlintstone.getRelatives().add(pebblesFlintstone);

		fredFlintstone.getTags().add(createMultiLevelTag(bf, createUniquePrefix(10)));

		fredFlintstone.setBigInt(BigInteger.TEN);

		fredFlintstone.getBigDec().add(BigDecimal.ZERO);
		fredFlintstone.getBigDec().add(BigDecimal.ONE);
		fredFlintstone.getBigDec().add(BigDecimal.TEN);

		fredFlintstone.setImage(createByteArr());

		fredFlintstone.getProperties().putAll(createProperties(createUniquePrefix(10)));

		return flintstonesFamily;
	}

	private static Address createFlintstonesAddress(BasicFactory bf) {
		return createAddress(bf, "301 Cobblestone Way", "Bedrock", "70777");
	}

	private static Person createFredFlintstone(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Fred", "Flintstone", GenderType.MALE, address);

		p.getContact().add(createHomePhonePersonContact(bf, p));
		p.getContact().add(createHomeMobilePersonContact(bf, p));
		p.getContact().add(createHomeWhatsAppPersonContact(bf, p));
		p.getContact().add(createHomeEmailPersonContact(bf, p));
		p.getContact().add(createHomeSkypePersonContact(bf, p));
		p.getContact().add(createHomeWebAddressPersonContact(bf, p));

		return p;
	}

	private static Person createWilmaFlintstone(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Wilma", "Flintstone", GenderType.FEMALE, address);

		p.getContact().add(createHomePhonePersonContact(bf, p));
		p.getContact().add(createHomeMobilePersonContact(bf, p));
		p.getContact().add(createHomeWhatsAppPersonContact(bf, p));
		p.getContact().add(createHomeEmailPersonContact(bf, p));
		p.getContact().add(createHomeSkypePersonContact(bf, p));
		p.getContact().add(createHomeWebAddressPersonContact(bf, p));

		return p;
	}

	private static Person createPebblesFlintstone(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Pebbles", "Flintstone", GenderType.FEMALE, address);

		return p;
	}

	private static Person createStonyFlintstone(BasicFactory bf, Address address) {
		Person p = createPerson(bf, "Stony", "Flintstone", GenderType.MALE, address);

		return p;
	}

	public static BusinessPerson createBusinessPerson(BasicFactory bf) {
		BusinessPerson bp = bf.createBusinessPerson();

		bp.setId(UUID.randomUUID().toString());
		bp.setFirstName("Thomas");
		bp.setLastName("Edison");
		bp.setGender(GenderType.MALE);

		bp.setCompanyIdCardNumber(UUID.randomUUID().toString());

		EmployeeInfo nikolaTesla = bf.createEmployeeInfo();
		nikolaTesla.setPosition("one-time employee");
		bp.getEmployeeInfo().add(nikolaTesla);

		return bp;
	}

	private static Person createPerson(BasicFactory bf, String firstName, String lastName, GenderType gender,
			Address address) {
		Person p = bf.createPerson();

		p.setId(UUID.randomUUID().toString());
		p.setFirstName(firstName);
		p.setLastName(lastName);
		p.setGender(gender);

		p.setAddress(address);

		return p;
	}

	private static Address createAddress(BasicFactory bf, String street, String city, String zip) {
		Address a = bf.createAddress();

		a.setId(UUID.randomUUID().toString());
		a.setStreet(street);
		a.setCity(city);
		a.setZip(zip);

		return a;
	}

	private static PersonContact createHomePhonePersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.PHONE, ContactContextType.HOME, UUID.randomUUID().toString(), p);
	}

	private static PersonContact createHomeMobilePersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.MOBILE, ContactContextType.HOME, UUID.randomUUID().toString(), p);
	}

	private static PersonContact createHomeWhatsAppPersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.WHATSAPP, ContactContextType.HOME, UUID.randomUUID().toString(), p);
	}

	private static PersonContact createHomeEmailPersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.EMAIL, ContactContextType.HOME, UUID.randomUUID().toString(), p);
	}

	private static PersonContact createHomeSkypePersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.SKYPE, ContactContextType.HOME, UUID.randomUUID().toString(), p);
	}

	private static PersonContact createHomeWebAddressPersonContact(BasicFactory bf, Person p) {
		return createPersonContact(bf, ContactType.WEBADDRESS, ContactContextType.HOME, UUID.randomUUID().toString(),
				p);
	}

	private static PersonContact createPersonContact(BasicFactory bf, ContactType type, ContactContextType context,
			String value, Person p) {
		PersonContact pc = bf.createPersonContact();

		pc.setContext(context);
		pc.setType(type);
		pc.setValue(value);

		pc.setContactPerson(p);

		return pc;
	}

	private static Tag createMultiLevelTag(BasicFactory bf, String namePrefix) {
		Tag t1 = createTag(bf, namePrefix, "tag_level_1", "tag_level_1_value", "tag_level_1_description");

		t1.setTag(createTag(bf, namePrefix, "tag_level_2", "tag_level_2_value", "tag_level_2_description"));

		t1.getTags().add(createTag(bf, namePrefix, "tag_level_3", "tag_level_3_value", "tag_level_3_description"));

		return t1;
	}

	private static Tag createTag(BasicFactory bf, String namePrefix, String name, String value, String description) {
		Tag t = bf.createTag();

		t.setName(namePrefix + "_" + name);
		t.setValue(value);
		t.setDescription(description);

		return t;
	}

	private static byte[] createByteArr() {
		byte[] b = new byte[20];
		new Random().nextBytes(b);
		return b;
	}

	private static Map<String, String> createProperties(String namePrefix) {
		Map<String, String> props = new HashMap<String, String>();

		props.put(createPropertyName(namePrefix, "prop_1"), "prop_1_value");
		props.put(createPropertyName(namePrefix, "prop_2"), "prop_2_value");
		props.put(createPropertyName(namePrefix, "prop_3"), "prop_3_value");
		props.put(createPropertyName(namePrefix, "prop_4"), "prop_4_value");

		return props;
	}

	private static String createPropertyName(String prefix, String name) {
		return (prefix + "_" + name);
	}

	public static ResourceSet createResourceSet(BasicPackage bp) {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(BasicPackage.eNS_URI, bp);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("test", new BasicResourceFactoryImpl());
		resourceSet.getResourceFactoryRegistry().getContentTypeToFactoryMap().put(BasicPackage.eCONTENT_TYPE,
				new BasicResourceFactoryImpl());
		return resourceSet;
	}

	private static String createUniquePrefix(int maxChars) {
		// @formatter:off
		return new RandomStringGenerator.Builder()
				.withinRange('a', 'z')
				.build()
				.generate(maxChars);
		// @formatter:on
	}
}
