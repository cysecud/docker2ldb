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
        DirectedBigraphBuilder bb = new DirectedBigraphBuilder(signature);
        Root r0 = bb.addRoot(); // root 0
        // parsing yaml config file
        InputStream input = new FileInputStream(new File("./etc/docker-compose.yml"));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        // build the bigraph
        int locality = 1;
        OuterName net = bb.addOuterNameOuterInterface(1, "net");
        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println("Service: " + service);
            bb.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            bb.addInnerNameInnerInterface(locality, "net", net); // add default net
            bb.addInnerNameOuterInterface(1, service, bb.addOuterNameInnerInterface(locality, service)); // expose the name
            if (services.get(service).get("expose") != null) {
                String port = (String) services.get(service).get("expose");
                System.out.println("Service exposes a port " + port + ", adding it to the interface.");
                bb.addOuterNameInnerInterface(locality, port);
            }
            if (services.get(service).get("ports") != null) {
                List<String> mappings = (List<String>) services.get(service).get("ports");
                for (String map : mappings) {
                    String[] r = map.split("\\:");
                    System.out.println("Service maps port " + r[1] + " to port "+ r[0] +", adding thme to interfaces.");
                    bb.addInnerNameOuterInterface(1, r[0], bb.addOuterNameInnerInterface(locality, r[1]));
                }
            }

            locality++;
        }
        System.out.println(bb);
    }
}
