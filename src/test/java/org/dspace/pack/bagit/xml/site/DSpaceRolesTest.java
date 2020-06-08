package org.dspace.pack.bagit.xml.site;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.google.common.collect.ImmutableSet;
import org.dspace.eperson.Group;
import org.junit.Test;

public class DSpaceRolesTest {

    @Test
    public void testOutput() throws JAXBException {
        DSpaceRoles dSpaceRoles = new DSpaceRoles();

        AssociatedGroup group = new AssociatedGroup();
        group.setId("1");
        group.setName(Group.ADMIN);

        Member member = new Member();
        member.setId("2");
        member.setName("admin@localhost");

        Member member1 = new Member();
        member1.setId("0");
        member1.setName(Group.ANONYMOUS);

        group.addMember(member);
        group.addMemberGroup(member1);
        dSpaceRoles.setGroups(ImmutableSet.of(group));

        Person person = new Person();
        person.setId("1")
              .setLanguage("en")
              .setNetId("person")
              .setEmail("person@localhost")
              .setFirstName("Person")
              .setLastName("McPerson")
              .selfRegistered()
              .canLogin();
        dSpaceRoles.addPerson(person);

        JAXBContext context = JAXBContext.newInstance(DSpaceRoles.class, AssociatedGroup.class, Member.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        marshaller.marshal(dSpaceRoles, System.out);

    }

}