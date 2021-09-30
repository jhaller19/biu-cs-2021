import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *	A program that allows the user to view the effect of "knocking out" or "overexpressing" every other node--or pairs of
 *	nodes--on a selected target node at a selected time step
 *
 * 	Interpreting the output:
 * 		1.  A "(+)" indicates that that there WERE solutions for the model when the node(s) listed was perturbated (either
 * 			KO'd or FE'd) and we anticipted that the <target node> would be ON at <time step> AND there were NO solutions
 * 			for the model where the node(s) listed was perturbated and we anticipated that the <target node> would be OFF
 * 			at <time step>. The implication of this result is that it gives us evidence that perturbating the
 * 			listed node(s) plays some role in the <target node> arriving at at the ON state at <time step>.
 *
 * 		2. 	A "(-)" indicates that that there WERE solutions for the model when the node(s) listed was perturbated (either
 *   		KO'd or FE'd) and we anticipted that the <target node> would be OFF at <time step> AND there were NO solutions
 *  		for the model where the node(s) listed was perturbated and we anticipated that the <target node> would be ON
 *   		at <time step>. The implication of this result is that it gives us evidence that perturbating the
 *   		listed node(s) plays some role in the <target node> arriving at at the OFF state at <time step>.
 *
 *  		3. 	"Inconclusive" indicates that there were solutions for both or neither of the anticipated states of
 *  		<target node> and therefore no conlcusion can be drawn as the perturbation of the listed node did not appear
 *  		to have a direct impact on <target node>
 *
 * 	Arguments:
 * 		<model file> <spec file> <mode> <single or double> <target node> <type of perturbation (KO or FE)> <time step>
 *
 * 	Example:
 * 		java PerturbationSimulator TestModels\perturbations_model\model.net TestModels\perturbations_model\observations.spec time_step single B KO 18
 *
 * @author Jonathan Haller
 *
 */
public class PerturbationSimulator {

	public static void main(String[] args) throws Exception {
		//Parse args
		if(args.length < 7 || args.length > 7){
			throw new Exception("Must be 7 Arguments");
		}
		String modelFileName = args[0];
		String specFileName = args[1];
		String mode = args[2];
		String nPertubations = args[3];
		String targetNode = args[4];
		String typeOfPerturbationArg = args[5];
		String typeOfPerturbation = typeOfPerturbationArg.equals("KO") ? "Knockout" : "Overexpress";
		String timeStep = args[6];

		//Error checking
		if(!nPertubations.equals("single") && !nPertubations.equals("double")){
			throw new Exception("Argument must be 'single' or 'double'");
		}
		if((!typeOfPerturbationArg.equals("KO")) && (!typeOfPerturbationArg.equals("FE"))){
			throw new Exception("Argument must be 'KO' or 'FE'");
		}

		//parse model to get a list of nodes
		List<String> nodes = parseModelNodes(modelFileName , typeOfPerturbationArg);

		//Get text of spec file as template
		StringBuilder specFileTemplate = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(specFileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				specFileTemplate.append(line + "\n");
			}
		}

		//Boot up NAE
		Runtime rt = Runtime.getRuntime();
		Process pr1 = rt.exec("javac NAE/*.java validate/*.java");
		Process pr2 = rt.exec("jar cvfm NAE.jar NAE/manifest.txt NAE/*.class validate/*.class");

		boolean onFileSolutionsExist = false; //Do solutions exist when the <target node> is expected to be ON at <time step>
		boolean offFileSolutionsExist = false; //Do solutions exist when the <target node> is expected to be OFF at <time step>
		StringBuilder out = new StringBuilder();

		if(nPertubations.equals("single")) {
			for (String node : nodes) {
				if (node.equals(targetNode)) continue;
				//Create spec file where target node is expected to be ON and run NAE
				String onFile = createSpecFile(nodes, specFileTemplate, node, 1, typeOfPerturbationArg,
						targetNode, typeOfPerturbation, timeStep, modelFileName, specFileName);
				onFileSolutionsExist = runNAE(onFile, modelFileName, rt, mode);

				//Create spec file where target node is expected to be OFF and run NAE
				String offFile = createSpecFile(nodes, specFileTemplate, node, 0, typeOfPerturbationArg,
						targetNode, typeOfPerturbation, timeStep, modelFileName, specFileName);
				offFileSolutionsExist = runNAE(offFile, modelFileName, rt, mode);
				//Process result to see if we have a prediction
				processResults(out, onFileSolutionsExist, offFileSolutionsExist, node);
			}
		}
		else{
			for(String node1 : nodes){
				for(String node2 : nodes){
					if(node1.equals(targetNode) || node2.equals(targetNode) || node2.equals(node1)) continue;
					//Create spec file where target node is expected to be ON and run NAE
					String onFile = createDoubleSpecFile(nodes, specFileTemplate, node1, node2, 1, typeOfPerturbationArg, targetNode,
							typeOfPerturbation, timeStep, modelFileName, specFileName);
					onFileSolutionsExist = runNAE(onFile, modelFileName, rt, mode);
					//Create spec file where target node is expected to be OFF and run NAE
					String offFile = createDoubleSpecFile(nodes, specFileTemplate, node1, node2, 0, typeOfPerturbationArg,
							targetNode, typeOfPerturbation, timeStep, modelFileName, specFileName);
					offFileSolutionsExist = runNAE(offFile, modelFileName, rt, mode);
					//Process result to see if we have a prediction
					processResults(out, onFileSolutionsExist, offFileSolutionsExist, node1 + " & " + node2);
				}
			}
		}

		printResults(targetNode , typeOfPerturbation, timeStep, out);
	}

	private static void processResults(StringBuilder out , boolean onFileSolutionsExist , boolean offFileSolutionsExist, String node){
		if(onFileSolutionsExist && !offFileSolutionsExist){
			out.append(String.format("  %-10s (+)\n",node + ":"));
		}else if(offFileSolutionsExist && !onFileSolutionsExist){
			out.append(String.format("  %-10s (-)\n",node + ":"));
		}else{
			out.append(String.format("  %-10s Inconclusive\n",node + ":"));
		}
	}

	private static void printResults(String targetNode , String typeOfPerturbation, String timeStep,StringBuilder out ) {
		System.out.println("***************************");
		System.out.println("Target Node: " + targetNode);
		System.out.println("Perturbation Type: " + typeOfPerturbation);
		System.out.println("Time Step: " + timeStep);
		System.out.println("***************************");
		System.out.println(out);
	}

	private static boolean runNAE(String completeSpecFile, String modelFile, Runtime rt, String mode) throws IOException {
		String[] args = new String[]{"java" , "-jar" ,  "NAE.jar",  "1", modelFile, completeSpecFile, mode};
		Process pr = rt.exec(args);
		BufferedReader reader=new BufferedReader(new InputStreamReader(pr.getInputStream()));
		String firstLine = null;
		String line;
		return !(firstLine = reader.readLine()).equals("No Solutions Found");
	}

	/**
	 * Given a node to be be perturbed, this method translates that perturbation into the appropriate spec file
	 * semantics and creates the file
	 *
	 * @return name of the spec file
	 * */
	private static String createSpecFile(List<String> nodes , StringBuilder specFileTemplate, String curPerturbedNode,
										 int value, String typeOfPerturbationArg, String targetNode, String typeOfPerturbation,
										 String timeStep, String modelFileName, String specFileName) throws Exception {

		StringBuilder curSpecFile = new StringBuilder(specFileTemplate);
		curSpecFile.append("\n//Perturbations Specs");
		//Append final result of target node macro
		curSpecFile.append("\n$" + typeOfPerturbation + "ResultExpression := {" + targetNode + " = "+ value + "};\n\n");
		//Append time steps
		curSpecFile.append("#" + curPerturbedNode + typeOfPerturbation + "Experiment[0] |= $" + curPerturbedNode + typeOfPerturbation + ";\n");
		curSpecFile.append("#" + curPerturbedNode + typeOfPerturbation + "Experiment["+ timeStep +"] |= $" + typeOfPerturbation + "ResultExpression;\n\n");
		//Append perturbation macro
		curSpecFile.append("$" + curPerturbedNode+ typeOfPerturbation + ":=\n{\n");
		int nodeCount = 0;
		for (String node : nodes) {
			if (nodeCount != 0) {
				curSpecFile.append(" and ");
			}
			if (node.equals(curPerturbedNode)) {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=1");
			} else {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=0");
			}
			curSpecFile.append("\n");
			nodeCount++;
		}
		curSpecFile.append("};\n");

		//Create and write to new spec file (perturbations.spec)
		String path = null;
		try {
			path = modelFileName.substring(0, modelFileName.lastIndexOf("/"));
		}catch (StringIndexOutOfBoundsException e){
			path = modelFileName.substring(0, modelFileName.lastIndexOf("\\"));
		}
		String fileType = specFileName.split("\\.")[1];
		String retFileName = path + File.separator + "perturbations." + fileType;
		PrintWriter pr = new PrintWriter(retFileName, "UTF-8");
		pr.print(curSpecFile.toString());
		pr.close();
		return retFileName;
	}

	/**
	 * Given a pair of nodes to be be perturbed, this method translates that perturbation into the appropriate .spec file
	 * semantics and creates the file
	 *
	 * @return name of the spec file
	 */
	private static String createDoubleSpecFile(List<String> nodes , StringBuilder specFileTemplate, String curPerturbedNode1, String curPerturbedNode2,
											   int value, String typeOfPerturbationArg, String targetNode, String typeOfPerturbation,
											   String timeStep, String modelFileName, String specFileName) throws Exception {
		StringBuilder curSpecFile = new StringBuilder(specFileTemplate);
		curSpecFile.append("\n//Perturbations Specs");
		//Append final result of target node macro
		curSpecFile.append("\n$" + typeOfPerturbation + "ResultExpression := {" + targetNode + " = "+ value + "};\n\n");
		//Append time steps
		curSpecFile.append("#" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + "Experiment[0] |= $" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + ";\n");
		curSpecFile.append("#" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + "Experiment["+ timeStep +"] |= $" + typeOfPerturbation + "ResultExpression;\n\n");
		//Append perturbation macro
		curSpecFile.append("$" + curPerturbedNode1 + "_" + curPerturbedNode2 + typeOfPerturbation + ":=\n{\n");
		int nodeCount = 0;
		for (String node : nodes) {
			if (nodeCount != 0) {
				curSpecFile.append(" and ");
			}
			if (node.equals(curPerturbedNode1) || node.equals(curPerturbedNode2)) {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=1");
			} else {
				curSpecFile.append("  " + typeOfPerturbationArg + "(" + node + ")=0");
			}
			curSpecFile.append("\n");
			nodeCount++;
		}
		curSpecFile.append("};\n");

		//Create and write to new spec file (perturbations.spec)
		String path = null;
		try {
			path = modelFileName.substring(0, modelFileName.lastIndexOf("/"));
		}catch (StringIndexOutOfBoundsException e){
			path = modelFileName.substring(0, modelFileName.lastIndexOf("\\"));
		}
		String fileType = specFileName.split("\\.")[1];
		String retFileName = path + File.separator + "perturbations." + fileType;
		PrintWriter pr = new PrintWriter(retFileName, "UTF-8");
		pr.print(curSpecFile.toString());
		pr.close();
		return retFileName;
	}

	static List<String> parseModelNodes(String modelFileName, String typeOfPerturbationArg) throws IOException {
		List<String> nodes = new ArrayList<>();
		BufferedReader model = readFile(modelFileName);
		//read through the file
		String line = null;
		line = model.readLine();
		//We don't care about "directives", only nodes
		while(line.startsWith("directive")) line = model.readLine();
		String[] clauses = line.trim().split(";");
		for(String c:clauses){
			//if this line contains a "(" it must be a node declaration
			StringBuilder nameOfNode = new StringBuilder();
			if(c.contains("(")){
				c.trim();
				for(int i = 0; i < c.length();i++){
					if((c.charAt(i) == '[' ) ||(c.charAt(i)== '(')){
						break;
					}
					nameOfNode.append(c.charAt(i));
				}
			}
			//Only add the node if it has a "+" or "-" corresponding to the perturbation argument
			if(c.split("\\[|\\]")[1].contains("-") && typeOfPerturbationArg.equals("KO")){
				nodes.add(nameOfNode.toString().trim());
			}else if(c.split("\\[|\\]")[1].contains("+") && typeOfPerturbationArg.equals("FE")){
				nodes.add(nameOfNode.toString().trim());
			}
		}
		return nodes;
	}

	//IO Helper Method
	static BufferedReader readFile(String file) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(file),"UTF-8"));
		//get rid of BOM at beginning of file.
		in.mark(1);
		if (in.read() != 0xFEFF)
			in.reset();
		return in;
	}

}
