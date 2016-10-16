package it.uniud.mads.docker2ldb;

import it.uniud.mads.jlibbig.core.ldb.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Docker2Ldb {

    public static void main(String args[]) {
        try {
            System.out.println("Composed bigraph: \n" + docker2ldb("./etc/docker-compose.yml"));
        } catch (FileNotFoundException e) {
            System.err.println("File not found");
        }
    }

    private static DirectedBigraph docker2ldb(String pathToYAML) throws FileNotFoundException {
        // preparing control and empty bigraph
        DirectedControl container = new DirectedControl("container_1", true, 1, 1);
        DirectedControl[] controls = {container};
        DirectedSignature signature = new DirectedSignature(controls);
        DirectedBigraphBuilder cmp = new DirectedBigraphBuilder(signature);

        Root r0 = cmp.addRoot(); // root 0
        System.out.println("Added a root to the bigraph.");

        // parsing yaml config file
        InputStream input = new FileInputStream(new File(pathToYAML));
        Yaml yaml = new Yaml();
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        Map<String, Map> networks = o.get("networks");
        System.out.println("YAML config file correctly loaded.");
        boolean default_net = true;

        // build the bigraph
        int locality = 1;
        List<DirectedBigraph> graphs = new ArrayList<>(services.size());
        Map<String, OuterName> nets = new HashMap<>();

        // networks
        if (networks != null) {
            default_net = false;

        } else {
            default_net = true;
            nets.put("default", cmp.addAscNameOuterInterface(1, "default"));
            System.out.println("Added default network.");
        }

        // save service names
        Map<String, OuterName> names = new HashMap<>();

        for (String service : services.keySet()) {
            cmp.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            if (default_net) {
                cmp.addAscNameInnerInterface(locality, "default", nets.get("default")); // add default net
            }
            names.put(service, cmp.addDescNameInnerInterface(locality, service));
            cmp.addDescNameOuterInterface(1, service, names.get(service)); // expose the name
            locality++;
        }

        locality = 1;
        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println("Service: " + service);

            DirectedBigraphBuilder current = new DirectedBigraphBuilder(signature);
            System.out.println("Creating a bigraph for the service.");
            Root currentRoot = current.addRoot(); // add a root
            Node node = current.addNode(container.getName(), currentRoot); // add a node of container type
            current.addSite(node); // add a site for future purposes

            current.addDescNameOuterInterface(1, service, node.getInPort(0).getEditable());
            // networks
            if (default_net) {
                node.getOutPort(0).getEditable().setHandle(current.addAscNameOuterInterface(1, "default").getEditable()); // link the net to the node
            } else {

            }
            // expose
            if (services.get(service).get("expose") != null) {
                List<String> ports = (List<String>) services.get(service).get("expose");
                for (String port : ports) {
                    System.out.println("Service exposes port " + port + ", adding it to the interface.");
                    current.addDescNameOuterInterface(1, port, current.addDescNameInnerInterface(1, port));
                    cmp.addDescNameInnerInterface(locality, port);
                }
            }
            // ports
            if (services.get(service).get("ports") != null) {
                List<String> mappings = (List<String>) services.get(service).get("ports");
                for (String map : mappings) {
                    String[] ps = map.split(":");
                    System.out.println("Service maps port " + ps[1] + " to port " + ps[0] + ", adding them to interfaces.");
                    current.addDescNameOuterInterface(1, ps[1], current.addDescNameInnerInterface(1, ps[1]));
                    cmp.addDescNameOuterInterface(1, ps[0], cmp.addDescNameInnerInterface(locality, ps[1]));
                }
            }
            // links
            if (services.get(service).get("links") != null) {
                List<String> links = (List<String>) services.get(service).get("links");
                for (String link : links) {
                    String[] ls = link.split(":");
                    if (ls.length > 1) {
                        System.out.println("Service links to container " + ls[0] + ", renaming it to " + ls[1] + " recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + ls[1], current.addAscNameOuterInterface(1, "l_" + ls[1]));
                        cmp.addAscNameInnerInterface(locality, "l_" + ls[1], names.get(ls[0]));
                    } else {
                        System.out.println("Service links to container " + link + ", recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + link, current.addAscNameOuterInterface(1, "l_" + link));
                        cmp.addAscNameInnerInterface(locality, "l_" + link, names.get(link));
                    }
                }
            }
            System.out.println("Resulting bigraph: \n" + current);
            System.out.println("----------------------------------------------");
            graphs.add(current.makeBigraph());
            locality++; // ready for the next
        }
        System.out.println("Compose bigraph: \n" + cmp);
        System.out.println("----------------------------------------------");
        List<DirectedBigraph> outs = new ArrayList<>();
        outs.add(cmp.makeBigraph());
        return DirectedBigraph.compose(outs, graphs);
    }
}
