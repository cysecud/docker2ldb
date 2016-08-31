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
        OuterName net = cmp.addOuterNameOuterInterface(1, "net");
        DirectedBigraphBuilder[] containers = new DirectedBigraphBuilder[services.size()];
        for (String service : services.keySet()) { // parse every service in docker-compose file
            DirectedBigraphBuilder serviceBigBuilder = new DirectedBigraphBuilder(signature);
            System.out.println("Creating a bigraph for the service.");
            Root serviceRoot = serviceBigBuilder.addRoot();
            serviceBigBuilder.addSite(serviceRoot);
            Node n = serviceBigBuilder.addNode(container.getName(), serviceRoot);
            OuterName on = serviceBigBuilder.addOuterNameOuterInterface(1, "net");
            n.getOutPort(0).getEditable().setHandle(on.getEditable());
            System.out.println("Service: " + service);
            cmp.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            cmp.addInnerNameInnerInterface(locality, "net", net); // add default net
            serviceBigBuilder.addInnerNameOuterInterface(1, service, n.getInPort(0));
            cmp.addInnerNameOuterInterface(1, service, cmp.addOuterNameInnerInterface(locality, service)); // expose the name
            // expose
            if (services.get(service).get("expose") != null) {
                String port = (String) services.get(service).get("expose");
                System.out.println("Service exposes a port " + port + ", adding it to the interface.");
                serviceBigBuilder.addInnerNameOuterInterface(1, port, serviceBigBuilder.addOuterNameInnerInterface(1, port));
                cmp.addOuterNameInnerInterface(locality, port);
            }
            // ports
            if (services.get(service).get("ports") != null) {
                List<String> mappings = (List<String>) services.get(service).get("ports");
                for (String map : mappings) {
                    String[] r = map.split(":");
                    System.out.println("Service maps port " + r[1] + " to port " + r[0] + ", adding them to interfaces.");
                    serviceBigBuilder.addInnerNameOuterInterface(1, r[1], serviceBigBuilder.addOuterNameInnerInterface(1, r[1]));
                    cmp.addInnerNameOuterInterface(1, r[0], cmp.addOuterNameInnerInterface(locality, r[1]));
                }
            }
            // links
            if (services.get(service).get("links") != null) {
                List<String> links = (List<String>) services.get(service).get("links");
                for (String link : links) {
                    System.out.println("Service links to container " + link + ", recreating this on interfaces.");
                    serviceBigBuilder.addInnerNameInnerInterface(1, "l_" + link, serviceBigBuilder.addOuterNameOuterInterface(1, "l_" + link));
                    cmp.addInnerNameInnerInterface(locality, "l_" + link);
                }
            }
            System.out.println("Service bigraph: " + serviceBigBuilder);
            containers[locality-1]=serviceBigBuilder;
            locality++; // ready for the next
        }
        //System.out.println(bb);
    }
}
