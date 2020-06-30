import {
  limitQuery,
  computeAttributes,
  validateQuery,
  getNeighbourAnswers,
} from '@/components/Visualiser/VisualiserUtils.js';

import CDB from '@/components/shared/CanvasDataBuilder';

import {
  getMockedEntityType,
  getMockedAttributeType,
  getMockedConceptMap,
  getMockedTransaction,
  getMockedEntity,
  getMockedRelation,
  getMockedAttribute,
  getMockedTransactionLazy,
  getMockedRole,
} from '../../../helpers/mockedConcepts';

global.graknTx = getMockedTransaction([]);

Array.prototype.flatMap = function flat(lambda) { return Array.prototype.concat.apply([], this.map(lambda)); };

jest.mock('@/components/Visualiser/RightBar/SettingsTab/DisplaySettings.js', () => ({
  getTypeLabels() { return []; },
}));

jest.mock('@/components/shared/PersistentStorage', () => ({
  get() { return 10; },
}));

describe('limit Query', () => {
  test('add offset and limit to query', () => {
    const query = 'match $x isa person; get;';
    const limited = limitQuery(query);
    expect(limited).toBe('match $x isa person; get; offset 0; limit 10;');
  });

  test('add offset to query already containing limit', () => {
    const query = 'match $x isa person; get; limit 40;';
    const limited = limitQuery(query);
    expect(limited).toBe('match $x isa person; get; offset 0; limit 40;');
  });

  test('add limit to query already containing offset', () => {
    const query = 'match $x isa person; get; offset 20;';
    const limited = limitQuery(query);
    expect(limited).toBe('match $x isa person; get; offset 20; limit 10;');
  });

  test('query already containing offset and limit does not get changed', () => {
    const query = 'match $x isa person; get; offset 0; limit 40;';
    const limited = limitQuery(query);
    expect(limited).toBe(query);
  });

  test('query already containing offset and limit in inverted order does not get changed', () => {
    const query = 'match $x isa person; get; offset 0; limit 40;';
    const limited = limitQuery(query);
    expect(limited).toBe(query);
  });

  test('query containing multi-line queries', () => {
    const query = `
    match $x isa person;
    $r($x, $y); get;`;
    const limited = limitQuery(query);
    expect(limited).toBe(`
    match $x isa person;
    $r($x, $y); get; offset 0; limit 10;`);
  });

  test('query containing multi-line get query', () => {
    const query = `
    match $x isa person;
    get
         $x;`;
    const limited = limitQuery(query);
    expect(limited).toBe(`
    match $x isa person;
    get
         $x; offset 0; limit 10;`);
  });

  test('query without get', () => {
    const query = 'match $x isa person;';
    const limited = limitQuery(query);
    expect(limited).toBe('match $x isa person;');
  });

  test('multiline query with only limit', () => {
    const query = `match $x isa person;
    get; limit 1;`;
    const limited = limitQuery(query);
    expect(limited).toBe(`match $x isa person;
    get; offset 0; limit 1;`);
  });
});

describe('Compute Attributes', () => {
  test('attach attributes to type', async () => {
    const attributeType = getMockedAttributeType({
      extraProps: {
        remote: {
          label: () => Promise.resolve('attribute-type'),
        },
      },
    });

    const entityType = getMockedEntityType({
      isRemote: true,
      extraProps: {
        remote: {
          attributes: () => Promise.resolve({ collect: () => Promise.resolve([attributeType.asRemote()]) }),
        },
      },
    });

    const answer = getMockedConceptMap([attributeType]);
    const graknTx = getMockedTransaction([answer], {
      getConcept: () => Promise.resolve(entityType),
    });

    const nodes = await computeAttributes([entityType], graknTx);
    expect(nodes[0].attributes).toHaveLength(1);
    const attrType = nodes[0].attributes[0];
    expect(attrType.type).toBe('attribute-type');
  });

  test('attach attributes to thing', async () => {
    const attribute = getMockedAttribute({
      extraProps: {
        remote: {
          type: () => Promise.resolve({ label: () => Promise.resolve('attribute-type') }),
        },
      },
    });
    const entity = getMockedEntity({
      isRemote: true,
      extraProps: {
        remote: {
          attributes: () => Promise.resolve({ collect: () => Promise.resolve([attribute.asRemote()]) }),
        },
      },
    });

    const answer = getMockedConceptMap([attribute]);
    const graknTx = getMockedTransaction([answer], {
      getConcept: () => Promise.resolve(entity),
    });

    const nodes = await computeAttributes([entity], graknTx);
    expect(nodes[0].attributes).toHaveLength(1);

    expect(nodes[0].attributes[0].type).toBe('attribute-type');
    expect(nodes[0].attributes[0].value).toBe('attribute-value');
  });
});

describe('Validate Query', () => {
  test('match get', async () => {
    const query = 'match $p isa person; get;';
    expect(() => { validateQuery(query); }).not.toThrow();

    const multilineQuery = 'match\n$p isa person;\nget;';
    expect(() => { validateQuery(multilineQuery); }).not.toThrow();
  });
  test('compute path', async () => {
    const query = 'compute path from V229424, to V446496;';
    expect(() => { validateQuery(query); }).not.toThrow();

    const multilineQuery = 'compute path\nfrom V229424,\nto V446496;';
    expect(() => { validateQuery(multilineQuery); }).not.toThrow();
  });
  test('insert', async () => {
    const query = 'insert $x isa emotion; $x "like";';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'insert\n$x isa emotion; $x "like";';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('match insert', async () => {
    const query = 'match $x ias person, has name "John"; $y isa person, has name "Mary"; insert $r (child: $x, parent: $y) isa parentship;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$x ias person, has name "John";\n$y isa person, has name "Mary";\ninsert $r (child: $x, parent: $y) isa parentship;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('match delete', async () => {
    const query = 'match $p isa person, has email "raphael.santos@gmail.com"; delete $p;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$p isa person, has email "raphael.santos@gmail.com";\ndelete $p;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('count', async () => {
    const query = 'match $sce isa school-course-enrollment, has score $sco; $sco > 7.0; get; count;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$sce isa school-course-enrollment, has score $sco; $sco > 7.0;\nget; count;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('sum', async () => {
    const query = 'match $org isa organisation, has name $orn; $orn "Medicely"; ($org) isa employment, has salary $sal; get $sal; sum $sal;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$org isa organisation, has name $orn; $orn "Medicely"; ($org) isa employment, has salary $sal;\nget $sal; sum $sal;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('max', async () => {
    const query = 'match $sch isa school, has ranking $ran; get $ran; max $ran;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$sch isa school, has ranking $ran;\nget $ran; max $ran;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('min', async () => {
    const query = 'match ($per) isa marriage; ($per) isa employment, has salary $sal; get $sal; min $sal;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n($per) isa marriage; ($per) isa employment, has salary $sal;\nget $sal; min $sal;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('mean', async () => {
    const query = 'match $emp isa employment, has salary $sal; get $sal; mean $sal;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$emp isa employment, has salary $sal;\nget $sal; mean $sal;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('median', async () => {
    const query = 'match ($per) isa school-course-enrollment, has score $sco; get $sco; median $sco;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n($per) isa school-course-enrollment, has score $sco;\nget $sco; median $sco;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('group', async () => {
    const query = 'match $per isa person; $scc isa school-course, has title $tit; (student: $per, enrolled-course: $scc) isa school-course-enrollment; get; group $tit;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$per isa person; $scc isa school-course, has title $tit; (student: $per, enrolled-course: $scc) isa school-course-enrollment;\nget; group $tit;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('group count', async () => {
    const query = 'match $per isa person; $scc isa school-course, has title $tit; (student: $per, enrolled-course: $scc) isa school-course-enrollment; get; group $tit; count;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'match\n$per isa person; $scc isa school-course, has title $tit;\nget; group $tit; count;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
  test('compute', async () => {
    const query = 'compute count in person;';
    expect(() => { validateQuery(query); }).toThrow();

    const multilineQuery = 'compute count in person;';
    expect(() => { validateQuery(multilineQuery); }).toThrow();
  });
});

describe('Neihbour Answers', () => {
  test('when target node is a type', async () => {
    const entityType = getMockedEntityType();
    const targetNode = CDB.getTypeNode(entityType, '');
    const entity = getMockedEntity();
    const neighbourAnswer = getMockedConceptMap([entity], ['neighbour-instance']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(1);
    expect(answers[0].map().get('neighbour-instance')).toEqual(entity);
  });

  test('when target node is a type and its only neighbour is already visualised', async () => {
    const entityType = getMockedEntityType();
    const targetNode = CDB.getTypeNode(entityType, '');
    const entity = getMockedEntity();
    const neighbourAnswer = getMockedConceptMap([entity], ['neighbour-instance']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [{ id: 'entity-type-id-entity-id-isa' }];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(0);
  });

  test('when target node is an entity instance', async () => {
    const entity = getMockedEntity();
    const targetNode = await CDB.getInstanceNode(entity, '');
    const relation = getMockedRelation();
    const role = getMockedRole({
      extraProps: {
        local: {
          label: () => 'some-role',
        },
      },
    });
    const neighbourAnswer = getMockedConceptMap([relation, role], ['neighbour-relation', 'target-entity-role']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(1);
    expect(answers[0].map().get('neighbour-relation')).toEqual(relation);
  });

  test('when target node is an entity instance and its only neighbour is already visualised', async () => {
    const entity = getMockedEntity();
    const targetNode = await CDB.getInstanceNode(entity, '');
    const relation = getMockedRelation();
    const role = getMockedRole({
      extraProps: {
        local: {
          label: () => 'some-role',
        },
      },
    });
    const neighbourAnswer = getMockedConceptMap([relation, role], ['neighbour-relation', 'target-entity-role']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [{ id: 'relation-id-entity-id-some-role' }];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(0);
  });

  test('when target node is an attribute instance', async () => {
    const attribute = getMockedAttribute();
    const targetNode = await CDB.getInstanceNode(attribute, '');
    const entity = getMockedEntity();
    const neighbourAnswer = getMockedConceptMap([entity], ['neighbour-owner']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(1);
    expect(answers[0].map().get('neighbour-owner')).toEqual(entity);
  });

  test('when target node is an attribute instance and its only neighbour is already visualised', async () => {
    const attribute = getMockedAttribute();
    const targetNode = await CDB.getInstanceNode(attribute, '');
    const entity = getMockedEntity();
    const neighbourAnswer = getMockedConceptMap([entity], ['neighbour-owner']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [{ id: 'entity-id-attribute-id-has' }];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(0);
  });

  test('when target node is a relation instance', async () => {
    const relation = getMockedRelation();
    const targetNode = await CDB.getInstanceNode(relation, '');
    const entity = getMockedEntity();
    const role = getMockedRole({
      extraProps: {
        local: {
          label: () => 'some-role',
        },
      },
    });
    const neighbourAnswer = getMockedConceptMap([entity, role], ['neighbour-player', 'neighbour-role']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(1);
    expect(answers[0].map().get('neighbour-player')).toEqual(entity);
  });

  test('when target node is a relation instance and its only neighbour is already visualised', async () => {
    const relation = getMockedRelation();
    const targetNode = await CDB.getInstanceNode(relation, '');
    const entity = getMockedEntity();
    const role = getMockedRole({
      extraProps: {
        local: {
          label: () => 'some-role',
        },
      },
    });
    const neighbourAnswer = getMockedConceptMap([entity, role], ['neighbour-player', 'neighbour-role']);
    const graknTx = getMockedTransactionLazy([neighbourAnswer]);
    const currentEdges = [{ id: 'relation-id-entity-id-some-role' }];
    const answers = await getNeighbourAnswers(targetNode, currentEdges, graknTx);
    expect(answers).toHaveLength(0);
  });
});
