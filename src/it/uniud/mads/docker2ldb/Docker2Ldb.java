package it.uniud.mads.docker2ldb;

import it.uniud.mads.jlibbig.core.ldb.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class Docker2Ldb {
    public static void main(String args[]) throws FileNotFoundException {
        // preparing control and empty bigraph
        DirectedControl container = new DirectedControl("container_1", true, 1, 1);
        DirectedControl[] controls = {container};
        DirectedSignature signature = new DirectedSignature(controls);
        DirectedBigraphBuilder cmp = new DirectedBigraphBuilder(signature);

        Root r0 = cmp.addRoot(); // root 0
        System.out.println("Added a root to the bigraph.");

        // parsing yaml config file
        InputStream input = new FileInputStream(new File("./etc/docker-compose.yml"));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");

        // build the bigraph
        int locality = 1;
        DirectedBigraphBuilder[] containers = new DirectedBigraphBuilder[services.size()];
        OuterName net = cmp.addOuterNameOuterInterface(1, "net");
        System.out.println("Added default network");

        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println("Service: " + service);
            cmp.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            cmp.addInnerNameInnerInterface(locality, "net", net); // add default net

            DirectedBigraphBuilder current = new DirectedBigraphBuilder(signature);
            System.out.println("Creating a bigraph for the service.");
            Root currentRoot = current.addRoot(); // add a root
            current.addSite(currentRoot); // add a site for future purposes
            Node node = current.addNode(container.getName(), currentRoot); // add a node of container type
            OuterName nameOuterInterface = current.addOuterNameOuterInterface(1, "net"); // add the name in the outer interface
            node.getOutPort(0).getEditable().setHandle(nameOuterInterface.getEditable()); // link the net to the node

            current.addInnerNameOuterInterface(1, service, node.getInPort(0).getEditable());
            cmp.addInnerNameOuterInterface(1, service, cmp.addOuterNameInnerInterface(locality, service)); // expose the name

            // expose
            if (services.get(service).get("expose") != null) {
                String port = (String) services.get(service).get("expose");
                System.out.println("Service exposes a port " + port + ", adding it to the interface.");
                current.addInnerNameOuterInterface(1, port, current.addOuterNameInnerInterface(1, port));
                cmp.addOuterNameInnerInterface(locality, port);
            }
            // ports
            if (services.get(service).get("ports") != null) {
                List<String> mappings = (List<String>) services.get(service).get("ports");
                for (String map : mappings) {
                    String[] r = map.split(":");
                    System.out.println("Service maps port " + r[1] + " to port " + r[0] + ", adding them to interfaces.");
                    current.addInnerNameOuterInterface(1, r[1], current.addOuterNameInnerInterface(1, r[1]));
                    cmp.addInnerNameOuterInterface(1, r[0], cmp.addOuterNameInnerInterface(locality, r[1]));
                }
            }
            // links
            if (services.get(service).get("links") != null) {
                List<String> links = (List<String>) services.get(service).get("links");
                for (String link : links) {
                    System.out.println("Service links to container " + link + ", recreating this on interfaces.");
                    current.addInnerNameInnerInterface(1, "l_" + link, current.addOuterNameOuterInterface(1, "l_" + link));
                    cmp.addInnerNameInnerInterface(locality, "l_" + link);
                }
            }
            //System.out.println("Service bigraph: " + current);
            containers[locality-1]=current;
            locality++; // ready for the next
        }
        for (DirectedBigraphBuilder bbb : containers) {
            System.out.println(bbb);
        }
        System.out.println(cmp);
    }
}
