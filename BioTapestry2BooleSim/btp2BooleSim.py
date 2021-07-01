'''
INPUT 1 -> .sif file detailing the edges of the network
INPUT 2 -> regulatory conditions specifications (rc-specs) file (printing-pages format?)
OUTPUT -> BooleSim readable rules-file

Steps:
0. Initialize data structures:
    - List of Nodes
    - Dictionary of type Node -> Set of Incoming Edges
        > this assumes an Edge object, contains info on the affecting node and the type of interaction
        > interactionType can be any of {REPRESSES=-1, NEUTRAL=0, PROMOTES=1}
1. Iterate through edges, and for each one, do the following:
    - add the affecting node and the affected node to the list of nodes (if not present)
        > includes parsing the nodeID based on format specified in BTP documentation
    - add the appropriate edge to the affected node's set of incoming edges in the dict
2. For each node in the dict keyset, do the following:
    - parse the regulatory condition associated with the node from the rc-specs file
    - translate the rc into a boolean expression, and add it to 

'''
import pprint as pp
from sympy.logic.boolalg import *
from sympy import *

DEFAULT_RC_SPEC = '8' # chose randomly

'''
CLASSES 
'''
class Edge:
    def __init__(self, fromNode, toNode, interaction_str):
        self.fromNode = fromNode
        self.toNode = toNode
        self.interaction_num = interaction_str_to_num(interaction_str)

    def __str__(self) -> str:
        interaction_str = interaction_num_to_str(self.interaction_num)
        return '<Edge from {} to {} with interaction={}>'.format(self.fromNode, self.toNode, interaction_str)

    def get_from_node(self):
        return self.fromNode
    def get_to_node(self):
        return self.toNode
    def get_interaction_num(self):
        return self.interaction_num

'''
HELPER METHODS
'''
def interaction_str_to_num(interaction_str):
    switcher = {
        'REPRESSES': -1,
        'NEUTRAL': 0,
        'PROMOTES': 1,
    }
    return switcher.get(interaction_str)

def interaction_num_to_str(interaction_num):
    switcher = {
        -1: 'REPRESSES',
        0: 'NEUTRAL',
        1: 'PROMOTES',
    }
    return switcher.get(interaction_num)

#TODO still unfinished, check btp docs
def parse_node_id(full_node_id):
    # replace spaces with underscores
    # 
    return full_node_id[full_node_id.find(':')+1:]

# translate from python boolean-logic syntax to BooleSim readable syntax
def clean_bool_expression(bool_expression):
    bool_expression = bool_expression.replace('&','&&')
    bool_expression = bool_expression.replace('~','!')
    return bool_expression

# takes in regulatory conditions specifications, along with the interactions of the node, 
# and translates those into a boolean expression readable by BooleSim  
def generate_bool_expression(rc_spec, incoming_edges):
    bool_expression = symbols('False')

    # categorize nodes by interaction
    activator_nodes = []
    repressor_nodes = []
    neutral_nodes = []
    for edge in incoming_edges:
        if edge.get_interaction_num() > 0: activator_nodes.append(edge.get_from_node())
        elif edge.get_interaction_num() < 0: repressor_nodes.append(edge.get_from_node())
        else: neutral_nodes.add(edge.get_from_node())
    print('Activator Nodes: '+pp.pformat(activator_nodes))
    print('Repressor Nodes: '+pp.pformat(repressor_nodes))
    print('Neutral Nodes: '+pp.pformat(neutral_nodes))

    # generate expression based on rc_spec
    # rc-0: AllActivators AND NoRepressors
    if rc_spec == '0':
        activator_expr = symbols('True')
        repressor_expr = symbols('True')
        for i in range(len(activator_nodes)):
            activator_expr = And(activator_expr,symbols(activator_nodes[i]))
        for i in range(len(repressor_nodes)):
            repressor_expr = And(repressor_expr,Not(symbols(repressor_nodes[i])))
        bool_expression = And(activator_expr, repressor_expr)
        bool_expression = simplify(str(bool_expression))
        return clean_bool_expression(str(bool_expression))

def main():
    # init data structures
    node_set = set()
    node_to_incoming_edges = {}
    rc_specs = {}
    boolesim_rules = {}
    
    sif_filename = input('Enter .sif filename: ')
    rc_specs_filename = input('Enter rc_specs filename: ')

    # iterate through .sif file
    with open(sif_filename, 'r') as f:
        for line in f.readlines():
            # parse the .sif line
            tokens = line.split('\t')
            from_node = parse_node_id(tokens[0])
            interaction_str = tokens[1]
            to_node = parse_node_id(tokens[2]).rstrip()
            edge = Edge(from_node, to_node, interaction_str)
            
            # update data structures
            node_set.update([from_node, to_node])
            if to_node in node_to_incoming_edges:
                node_to_incoming_edges[to_node].add(edge)
            else:
                node_to_incoming_edges[to_node] = {edge}
    pp.pprint(node_set)
    pp.pprint(node_to_incoming_edges)

    # initialize default rc's for each node
    for node in node_set:
        rc_specs[node] = DEFAULT_RC_SPEC
    # iterate through rc_specs and parse
    with open(rc_specs_filename, 'r') as f:
        for line in f.readlines():
            tokens = line.split('\t')
            node_id = tokens[0]
            rc_spec = tokens[1].rstrip()
            rc_specs[node_id] = rc_spec

    pp.pprint(rc_specs)

    # generate BooleSim rules for all nodes, using the rc_specs
    for node, incoming_edges in node_to_incoming_edges.items():
        boolesim_rules[node] = generate_bool_expression(rc_specs[node], incoming_edges)

    pp.pprint(boolesim_rules)

main()


