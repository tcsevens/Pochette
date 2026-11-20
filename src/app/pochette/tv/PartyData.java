package app.pochette.tv;

/** Words and questions of the party games, in French, English and Spanish (same order in each language). */
final class PartyData {
    private PartyData() { }

    /** question | A | B | C | D | index of the right answer */
    private static final String[][] QUIZ = {
        {   // fr
            "Quelle est la capitale de l'Australie ?|Sydney|Canberra|Melbourne|Perth|1",
            "Combien de côtés a un hexagone ?|5|6|7|8|1",
            "Quelle planète est la plus proche du Soleil ?|Vénus|Mars|Mercure|La Terre|2",
            "Qui a peint la Joconde ?|Van Gogh|Léonard de Vinci|Picasso|Monet|1",
            "Quel est le plus grand océan ?|Atlantique|Indien|Arctique|Pacifique|3",
            "En quelle année l'homme a-t-il marché sur la Lune ?|1959|1969|1979|1989|1",
            "Quel est le symbole chimique de l'or ?|Ag|Au|Or|Fe|1",
            "Dans quel pays se trouve le Taj Mahal ?|Inde|Pakistan|Iran|Égypte|0",
            "Combien de joueurs une équipe de football a-t-elle sur le terrain ?|9|10|11|12|2",
            "Quel est le plus long fleuve de France ?|La Seine|Le Rhône|La Garonne|La Loire|3",
            "Qui a écrit « Les Misérables » ?|Victor Hugo|Émile Zola|Balzac|Flaubert|0",
            "Quel est l'animal terrestre le plus rapide ?|Le lion|Le guépard|L'antilope|Le cheval|1",
            "Combien d'os compte le corps d'un adulte, environ ?|106|206|306|406|1",
            "Quel gaz les plantes absorbent-elles pour vivre ?|Oxygène|Azote|Dioxyde de carbone|Hélium|2",
            "Quelle est la capitale du Canada ?|Toronto|Montréal|Ottawa|Vancouver|2",
            "Qui a écrit « Roméo et Juliette » ?|Molière|Shakespeare|Dickens|Cervantès|1",
            "Quelle est la plus haute montagne du monde ?|K2|L'Everest|Le Mont Blanc|Le Kilimandjaro|1",
            "Combien de couleurs compte l'arc-en-ciel ?|5|6|7|8|2",
            "Dans quel pays se trouve la tour de Pise ?|France|Italie|Espagne|Grèce|1",
            "Quel instrument a 88 touches ?|L'orgue|L'accordéon|Le piano|La harpe|2",
            "Quel est le plus grand désert chaud du monde ?|Gobi|Sahara|Kalahari|Atacama|1",
            "Combien de minutes y a-t-il dans une journée ?|1240|1440|1640|2400|1",
            "Qui a dessiné Tintin ?|Hergé|Uderzo|Goscinny|Franquin|0",
            "Quelle est la monnaie du Japon ?|Le won|Le yuan|Le yen|Le baht|2",
            "Quelle mer sépare l'Europe de l'Afrique ?|La mer Noire|La Méditerranée|La Baltique|La mer du Nord|1"
        },
        {   // en
            "What is the capital of Australia?|Sydney|Canberra|Melbourne|Perth|1",
            "How many sides does a hexagon have?|5|6|7|8|1",
            "Which planet is closest to the Sun?|Venus|Mars|Mercury|Earth|2",
            "Who painted the Mona Lisa?|Van Gogh|Leonardo da Vinci|Picasso|Monet|1",
            "What is the largest ocean?|Atlantic|Indian|Arctic|Pacific|3",
            "In which year did humans first walk on the Moon?|1959|1969|1979|1989|1",
            "What is the chemical symbol for gold?|Ag|Au|Go|Fe|1",
            "In which country is the Taj Mahal?|India|Pakistan|Iran|Egypt|0",
            "How many players does a football (soccer) team have on the pitch?|9|10|11|12|2",
            "What is the longest river in France?|The Seine|The Rhône|The Garonne|The Loire|3",
            "Who wrote \"Les Misérables\"?|Victor Hugo|Émile Zola|Balzac|Flaubert|0",
            "What is the fastest land animal?|Lion|Cheetah|Antelope|Horse|1",
            "Roughly how many bones does an adult body have?|106|206|306|406|1",
            "Which gas do plants absorb to live?|Oxygen|Nitrogen|Carbon dioxide|Helium|2",
            "What is the capital of Canada?|Toronto|Montreal|Ottawa|Vancouver|2",
            "Who wrote \"Romeo and Juliet\"?|Molière|Shakespeare|Dickens|Cervantes|1",
            "What is the highest mountain in the world?|K2|Everest|Mont Blanc|Kilimanjaro|1",
            "How many colours are in a rainbow?|5|6|7|8|2",
            "In which country is the Leaning Tower of Pisa?|France|Italy|Spain|Greece|1",
            "Which instrument has 88 keys?|Organ|Accordion|Piano|Harp|2",
            "What is the largest hot desert in the world?|Gobi|Sahara|Kalahari|Atacama|1",
            "How many minutes are there in a day?|1240|1440|1640|2400|1",
            "Who drew Tintin?|Hergé|Uderzo|Goscinny|Franquin|0",
            "What is the currency of Japan?|Won|Yuan|Yen|Baht|2",
            "Which sea separates Europe from Africa?|The Black Sea|The Mediterranean|The Baltic|The North Sea|1"
        },
        {   // es
            "¿Cuál es la capital de Australia?|Sídney|Canberra|Melbourne|Perth|1",
            "¿Cuántos lados tiene un hexágono?|5|6|7|8|1",
            "¿Qué planeta está más cerca del Sol?|Venus|Marte|Mercurio|La Tierra|2",
            "¿Quién pintó la Mona Lisa?|Van Gogh|Leonardo da Vinci|Picasso|Monet|1",
            "¿Cuál es el océano más grande?|Atlántico|Índico|Ártico|Pacífico|3",
            "¿En qué año pisó el ser humano la Luna?|1959|1969|1979|1989|1",
            "¿Cuál es el símbolo químico del oro?|Ag|Au|Or|Fe|1",
            "¿En qué país está el Taj Mahal?|India|Pakistán|Irán|Egipto|0",
            "¿Cuántos jugadores tiene un equipo de fútbol en el campo?|9|10|11|12|2",
            "¿Cuál es el río más largo de Francia?|El Sena|El Ródano|El Garona|El Loira|3",
            "¿Quién escribió «Los miserables»?|Victor Hugo|Émile Zola|Balzac|Flaubert|0",
            "¿Cuál es el animal terrestre más rápido?|El león|El guepardo|El antílope|El caballo|1",
            "¿Cuántos huesos tiene un adulto, aproximadamente?|106|206|306|406|1",
            "¿Qué gas absorben las plantas para vivir?|Oxígeno|Nitrógeno|Dióxido de carbono|Helio|2",
            "¿Cuál es la capital de Canadá?|Toronto|Montreal|Ottawa|Vancouver|2",
            "¿Quién escribió «Romeo y Julieta»?|Molière|Shakespeare|Dickens|Cervantes|1",
            "¿Cuál es la montaña más alta del mundo?|K2|El Everest|El Mont Blanc|El Kilimanjaro|1",
            "¿Cuántos colores tiene el arcoíris?|5|6|7|8|2",
            "¿En qué país está la torre de Pisa?|Francia|Italia|España|Grecia|1",
            "¿Qué instrumento tiene 88 teclas?|El órgano|El acordeón|El piano|El arpa|2",
            "¿Cuál es el desierto cálido más grande del mundo?|Gobi|Sáhara|Kalahari|Atacama|1",
            "¿Cuántos minutos tiene un día?|1240|1440|1640|2400|1",
            "¿Quién dibujó a Tintín?|Hergé|Uderzo|Goscinny|Franquin|0",
            "¿Cuál es la moneda de Japón?|El won|El yuan|El yen|El baht|2",
            "¿Qué mar separa Europa de África?|El mar Negro|El Mediterráneo|El Báltico|El mar del Norte|1"
        }
    };


    /** France questions (fr, en, es) */
    private static final String[][] FRA = {
        {
            "Quel est le plus haut sommet de France ?|Le mont Blanc|Le mont Viso|Le pic du Midi|Le puy de Dôme|0",
            "En quelle année a eu lieu la prise de la Bastille ?|1689|1789|1799|1889|1",
            "Qui fut le premier président de la Ve République ?|Georges Pompidou|Charles de Gaulle|François Mitterrand|Valéry Giscard d'Estaing|1",
            "Combien la France métropolitaine compte-t-elle de régions ?|9|13|18|22|1",
            "Quelle est la capitale de la Bretagne ?|Nantes|Brest|Rennes|Vannes|2",
            "Pour quelle occasion la tour Eiffel a-t-elle été construite ?|L'Exposition universelle de 1889|Les Jeux olympiques de 1900|Le centenaire de Napoléon|La fin de la guerre de 1870|0",
            "Quel pays a offert la statue de la Liberté aux États-Unis ?|L'Angleterre|La France|L'Espagne|L'Italie|1",
            "De quelle région vient le camembert ?|La Normandie|L'Auvergne|La Savoie|La Bretagne|0",
            "Quelle ville est surnommée la « Ville rose » ?|Marseille|Bordeaux|Toulouse|Lyon|2",
            "Dans quelle ville se trouve le palais des Papes ?|Arles|Avignon|Nîmes|Orange|1",
            "Comment s'appelle l'hymne national français ?|Le Chant des Partisans|La Marseillaise|Le Chant du départ|La Parisienne|1",
            "Qui a écrit « Le Petit Prince » ?|Antoine de Saint-Exupéry|Albert Camus|Jacques Prévert|Jean-Paul Sartre|0",
            "Quelle île française est surnommée l'« île de Beauté » ?|La Corse|Belle-Île|L'île de Ré|Ouessant|0",
            "Quel département porte le numéro 75 ?|Le Rhône|Le Nord|Paris|La Gironde|2",
            "Quelle bataille de 1815 a mis fin à l'Empire de Napoléon ?|Austerlitz|Waterloo|Trafalgar|Verdun|1",
            "Quel plat est une spécialité d'Alsace ?|La bouillabaisse|Le cassoulet|La ratatouille|La choucroute|3",
            "Quelle ville est célèbre pour sa bouillabaisse ?|Lille|Nantes|Marseille|Dijon|2",
            "Quel peintre a peint des nymphéas à Giverny ?|Claude Monet|Auguste Renoir|Paul Cézanne|Edgar Degas|0",
            "Quel musée parisien abrite la Joconde ?|Le musée d'Orsay|Le Louvre|Le centre Pompidou|Le musée Rodin|1",
            "Où se termine traditionnellement le Tour de France ?|Sur les Champs-Élysées|À Lyon|Au Stade de France|À Montmartre|0",
            "Quelle ville était la capitale des Gaules ?|Lyon|Reims|Metz|Orléans|0",
            "En quelle année la France a-t-elle gagné sa première Coupe du monde de football ?|1990|1998|2002|2006|1",
            "Quel héros gaulois est le meilleur ami d'Obélix ?|Astérix|Tintin|Lucky Luke|Spirou|0",
            "Quel fleuve traverse Paris ?|La Loire|Le Rhône|La Seine|La Garonne|2"
        },
        {
            "What is the highest mountain in France?|Mont Blanc|Monte Viso|Pic du Midi|Puy de Dôme|0",
            "In which year was the Bastille stormed?|1689|1789|1799|1889|1",
            "Who was the first president of the Fifth Republic?|Georges Pompidou|Charles de Gaulle|François Mitterrand|Valéry Giscard d'Estaing|1",
            "How many regions does mainland France have?|9|13|18|22|1",
            "What is the capital of Brittany?|Nantes|Brest|Rennes|Vannes|2",
            "For which event was the Eiffel Tower built?|The 1889 World's Fair|The 1900 Olympics|Napoleon's centenary|The end of the 1870 war|0",
            "Which country gave the Statue of Liberty to the United States?|England|France|Spain|Italy|1",
            "Which region does camembert come from?|Normandy|Auvergne|Savoy|Brittany|0",
            "Which city is nicknamed the \"Pink City\"?|Marseille|Bordeaux|Toulouse|Lyon|2",
            "In which city is the Palais des Papes (Popes' Palace)?|Arles|Avignon|Nîmes|Orange|1",
            "What is the French national anthem called?|Le Chant des Partisans|La Marseillaise|Le Chant du départ|La Parisienne|1",
            "Who wrote \"The Little Prince\"?|Antoine de Saint-Exupéry|Albert Camus|Jacques Prévert|Jean-Paul Sartre|0",
            "Which French island is nicknamed the \"Island of Beauty\"?|Corsica|Belle-Île|Île de Ré|Ouessant|0",
            "Which French département has the number 75?|Rhône|Nord|Paris|Gironde|2",
            "Which 1815 battle ended Napoleon's empire?|Austerlitz|Waterloo|Trafalgar|Verdun|1",
            "Which dish is a speciality of Alsace?|Bouillabaisse|Cassoulet|Ratatouille|Choucroute|3",
            "Which city is famous for its bouillabaisse?|Lille|Nantes|Marseille|Dijon|2",
            "Which painter painted water lilies at Giverny?|Claude Monet|Auguste Renoir|Paul Cézanne|Edgar Degas|0",
            "Which Paris museum holds the Mona Lisa?|Musée d'Orsay|The Louvre|Centre Pompidou|Musée Rodin|1",
            "Where does the Tour de France traditionally finish?|On the Champs-Élysées|In Lyon|At the Stade de France|In Montmartre|0",
            "Which city was the capital of Roman Gaul?|Lyon|Reims|Metz|Orléans|0",
            "In which year did France win its first football World Cup?|1990|1998|2002|2006|1",
            "Which Gaulish hero is Obelix's best friend?|Asterix|Tintin|Lucky Luke|Spirou|0",
            "Which river flows through Paris?|The Loire|The Rhône|The Seine|The Garonne|2"
        },
        {
            "¿Cuál es la montaña más alta de Francia?|El Mont Blanc|El Monviso|El pico del Midi|El Puy de Dôme|0",
            "¿En qué año fue la toma de la Bastilla?|1689|1789|1799|1889|1",
            "¿Quién fue el primer presidente de la Quinta República?|Georges Pompidou|Charles de Gaulle|François Mitterrand|Valéry Giscard d'Estaing|1",
            "¿Cuántas regiones tiene la Francia metropolitana?|9|13|18|22|1",
            "¿Cuál es la capital de Bretaña?|Nantes|Brest|Rennes|Vannes|2",
            "¿Para qué ocasión se construyó la torre Eiffel?|La Exposición Universal de 1889|Los Juegos Olímpicos de 1900|El centenario de Napoleón|El fin de la guerra de 1870|0",
            "¿Qué país regaló la estatua de la Libertad a Estados Unidos?|Inglaterra|Francia|España|Italia|1",
            "¿De qué región viene el camembert?|Normandía|Auvernia|Saboya|Bretaña|0",
            "¿Qué ciudad se conoce como la «Ciudad Rosa»?|Marsella|Burdeos|Toulouse|Lyon|2",
            "¿En qué ciudad está el Palacio de los Papas?|Arlés|Aviñón|Nimes|Orange|1",
            "¿Cómo se llama el himno nacional francés?|El Canto de los Partisanos|La Marsellesa|El Canto de la Partida|La Parisina|1",
            "¿Quién escribió «El Principito»?|Antoine de Saint-Exupéry|Albert Camus|Jacques Prévert|Jean-Paul Sartre|0",
            "¿Qué isla francesa se llama la «Isla de la Belleza»?|Córcega|Belle-Île|Isla de Ré|Ouessant|0",
            "¿Qué departamento francés lleva el número 75?|Ródano|Norte|París|Gironda|2",
            "¿Qué batalla de 1815 puso fin al imperio de Napoleón?|Austerlitz|Waterloo|Trafalgar|Verdún|1",
            "¿Qué plato es una especialidad de Alsacia?|Bullabesa|Cassoulet|Ratatouille|Chucrut|3",
            "¿Qué ciudad es famosa por su bullabesa?|Lille|Nantes|Marsella|Dijon|2",
            "¿Qué pintor pintó los nenúfares en Giverny?|Claude Monet|Auguste Renoir|Paul Cézanne|Edgar Degas|0",
            "¿Qué museo de París guarda la Mona Lisa?|Museo de Orsay|El Louvre|Centro Pompidou|Museo Rodin|1",
            "¿Dónde termina tradicionalmente el Tour de Francia?|En los Campos Elíseos|En Lyon|En el Stade de France|En Montmartre|0",
            "¿Qué ciudad fue la capital de la Galia romana?|Lyon|Reims|Metz|Orleans|0",
            "¿En qué año ganó Francia su primer Mundial de fútbol?|1990|1998|2002|2006|1",
            "¿Qué héroe galo es el mejor amigo de Obélix?|Astérix|Tintín|Lucky Luke|Spirou|0",
            "¿Qué río atraviesa París?|El Loira|El Ródano|El Sena|El Garona|2"
        }
    };

    /** Québec questions (fr, en, es) */
    private static final String[][] QUE = {
        {
            "Quelle est la capitale du Québec ?|Montréal|Québec|Gatineau|Trois-Rivières|1",
            "Quelle est la plus grande ville du Québec ?|Québec|Laval|Montréal|Sherbrooke|2",
            "Quelle est la devise du Québec ?|Je me souviens|Liberté, égalité, fraternité|D'un océan à l'autre|Fier et libre|0",
            "Quel produit le Québec fournit-il en grande partie au monde ?|Le sirop d'érable|Le miel|Le blé|Le café|0",
            "Quels sont les trois ingrédients d'une poutine ?|Frites, fromage en grains, sauce|Pommes de terre, crème, lardons|Frites, mayonnaise, oignons|Pâtes, tomates, fromage|0",
            "Quelle est la fleur emblème du Québec ?|L'iris versicolore|La marguerite|Le tournesol|La tulipe|0",
            "Quelle équipe de la LNH est basée à Montréal ?|Les Nordiques|Les Maple Leafs|Les Canadiens|Les Bruins|2",
            "Comment appelle-t-on au Québec la pièce de un dollar canadien ?|Un castor|Un huard|Un orignal|Un caribou|1",
            "Qui est la mascotte du Carnaval de Québec ?|Bonhomme Carnaval|Pierrot|Monsieur Hiver|Le Père Janvier|0",
            "Dans quelle ville se trouve le Château Frontenac ?|Montréal|Sherbrooke|Rimouski|Québec|3",
            "Qui a fondé la ville de Québec en 1608 ?|Jacques Cartier|Samuel de Champlain|Paul de Chomedey de Maisonneuve|Louis Riel|1",
            "Qui a fondé Montréal (Ville-Marie) en 1642 ?|Samuel de Champlain|Jacques Cartier|Paul de Chomedey de Maisonneuve|Louis Joliet|2",
            "Quel grand événement Montréal a-t-elle accueilli en 1967 ?|L'Exposition universelle (Expo 67)|Les Jeux olympiques|Le Grand Prix|Les Jeux de la Francophonie|0",
            "Quelle montagne domine le centre de Montréal ?|Le mont Tremblant|Le mont Royal|Le mont Orford|Le mont Saint-Hilaire|1",
            "Quel festival d'humour se tient chaque été à Montréal ?|Juste pour rire|Les Francofolies|Osheaga|Le Grand Rire|0",
            "Quelle chanteuse québécoise a chanté « My Heart Will Go On » ?|Diane Dufresne|Céline Dion|Ginette Reno|Isabelle Boulay|1",
            "Quel dessert québécois est un gâteau nappé de sirop ?|Le pouding chômeur|La tarte Tatin|Le far breton|Le clafoutis|0",
            "Dans quelle région se trouve le rocher Percé ?|L'Abitibi|Charlevoix|La Gaspésie|Le Saguenay|2",
            "Dans quelle région se trouve le mont Tremblant ?|Les Laurentides|La Gaspésie|Charlevoix|L'Estrie|0",
            "Quel jour célèbre-t-on la Fête nationale du Québec ?|Le 1er juillet|Le 24 juin|Le 1er juin|Le 21 juin|1",
            "Quel fleuve traverse Québec et Montréal ?|Le Saint-Laurent|L'Outaouais|Le Saguenay|Le Richelieu|0",
            "Quel sport pratique le CF Montréal ?|Le baseball|Le soccer|Le basketball|Le hockey|1",
            "Sur quelle île est bâtie la ville de Montréal ?|L'île d'Orléans|L'île de Montréal|L'île Bizard|L'île Sainte-Hélène|1",
            "Quelle province borde le Québec à l'ouest ?|Le Manitoba|L'Ontario|Le Nouveau-Brunswick|La Saskatchewan|1",
            "Au Québec, que veut dire « magasiner » ?|Faire du shopping|Ranger un magasin|Vendre des produits|Voler à l'étalage|0",
            "Que désigne un « dépanneur » au Québec ?|Un garagiste|Une épicerie de quartier|Un plombier|Un taxi|1",
            "Au Québec, quel repas appelle-t-on le « souper » ?|Le repas du soir|Le repas de minuit|Le petit-déjeuner|Le goûter|0",
            "Au Québec, que veut dire « chum » ?|Un ami ou un petit ami|Un voisin|Un chien|Un patron|0",
            "Au Québec, comment appelle-t-on une voiture ?|Un char|Un wagon|Un camion|Une bagnole d'Europe|0"
        },
        {
            "What is the capital of Quebec?|Montreal|Quebec City|Gatineau|Trois-Rivières|1",
            "What is the largest city in Quebec?|Quebec City|Laval|Montreal|Sherbrooke|2",
            "What is Quebec's motto?|Je me souviens (I remember)|Liberty, equality, fraternity|From sea to sea|Proud and free|0",
            "Quebec supplies a large share of the world's...?|Maple syrup|Honey|Wheat|Coffee|0",
            "What are the three basic ingredients of poutine?|Fries, cheese curds, gravy|Potatoes, cream, bacon|Fries, mayonnaise, onions|Pasta, tomatoes, cheese|0",
            "What is Quebec's floral emblem?|Blue flag iris|Daisy|Sunflower|Tulip|0",
            "Which NHL team is based in Montreal?|The Nordiques|The Maple Leafs|The Canadiens|The Bruins|2",
            "What do Quebecers call the Canadian one-dollar coin?|A beaver|A loonie (huard)|A moose|A caribou|1",
            "Who is the mascot of the Quebec Winter Carnival?|Bonhomme Carnaval|Pierrot|Mister Winter|Father January|0",
            "In which city is the Château Frontenac?|Montreal|Sherbrooke|Rimouski|Quebec City|3",
            "Who founded Quebec City in 1608?|Jacques Cartier|Samuel de Champlain|Paul de Chomedey de Maisonneuve|Louis Riel|1",
            "Who founded Montreal (Ville-Marie) in 1642?|Samuel de Champlain|Jacques Cartier|Paul de Chomedey de Maisonneuve|Louis Joliet|2",
            "Which big event did Montreal host in 1967?|The World's Fair (Expo 67)|The Olympic Games|The Grand Prix|The Francophonie Games|0",
            "Which mountain rises in the middle of Montreal?|Mont Tremblant|Mount Royal|Mont Orford|Mont Saint-Hilaire|1",
            "Which comedy festival takes place in Montreal every summer?|Just for Laughs|Les Francofolies|Osheaga|Le Grand Rire|0",
            "Which Quebec singer sang \"My Heart Will Go On\"?|Diane Dufresne|Céline Dion|Ginette Reno|Isabelle Boulay|1",
            "Which Quebec dessert is a cake covered in syrup?|Pouding chômeur|Tarte Tatin|Far breton|Clafoutis|0",
            "In which region is Percé Rock?|Abitibi|Charlevoix|Gaspésie|Saguenay|2",
            "In which region is Mont Tremblant?|The Laurentians|Gaspésie|Charlevoix|Eastern Townships|0",
            "On which day is Quebec's national holiday?|1 July|24 June|1 June|21 June|1",
            "Which river flows past Quebec City and Montreal?|The St. Lawrence|The Ottawa|The Saguenay|The Richelieu|0",
            "Which sport does CF Montréal play?|Baseball|Soccer|Basketball|Hockey|1",
            "On which island is the city of Montreal built?|Île d'Orléans|Island of Montreal|Île Bizard|Île Sainte-Hélène|1",
            "Which province borders Quebec to the west?|Manitoba|Ontario|New Brunswick|Saskatchewan|1"
        },
        {
            "¿Cuál es la capital de Quebec?|Montreal|Quebec|Gatineau|Trois-Rivières|1",
            "¿Cuál es la ciudad más grande de Quebec?|Quebec|Laval|Montreal|Sherbrooke|2",
            "¿Cuál es el lema de Quebec?|Je me souviens (Me acuerdo)|Libertad, igualdad, fraternidad|De mar a mar|Orgulloso y libre|0",
            "¿De qué produce Quebec buena parte del mundo?|Sirope de arce|Miel|Trigo|Café|0",
            "¿Cuáles son los tres ingredientes de la poutine?|Patatas fritas, queso en grano, salsa|Patatas, nata, beicon|Patatas fritas, mayonesa, cebolla|Pasta, tomate, queso|0",
            "¿Cuál es la flor emblema de Quebec?|El lirio azul|La margarita|El girasol|El tulipán|0",
            "¿Qué equipo de la NHL tiene sede en Montreal?|Los Nordiques|Los Maple Leafs|Los Canadiens|Los Bruins|2",
            "¿Cómo llaman en Quebec a la moneda de un dólar canadiense?|Un castor|Un colimbo (huard)|Un alce|Un caribú|1",
            "¿Quién es la mascota del Carnaval de Quebec?|Bonhomme Carnaval|Pierrot|Señor Invierno|Papá Enero|0",
            "¿En qué ciudad está el Château Frontenac?|Montreal|Sherbrooke|Rimouski|Quebec|3",
            "¿Quién fundó la ciudad de Quebec en 1608?|Jacques Cartier|Samuel de Champlain|Paul de Chomedey de Maisonneuve|Louis Riel|1",
            "¿Quién fundó Montreal (Ville-Marie) en 1642?|Samuel de Champlain|Jacques Cartier|Paul de Chomedey de Maisonneuve|Louis Joliet|2",
            "¿Qué gran evento acogió Montreal en 1967?|La Exposición Universal (Expo 67)|Los Juegos Olímpicos|El Gran Premio|Los Juegos de la Francofonía|0",
            "¿Qué montaña domina el centro de Montreal?|El monte Tremblant|El monte Royal|El monte Orford|El monte Saint-Hilaire|1",
            "¿Qué festival de humor se celebra cada verano en Montreal?|Just for Laughs|Les Francofolies|Osheaga|Le Grand Rire|0",
            "¿Qué cantante quebequesa cantó «My Heart Will Go On»?|Diane Dufresne|Céline Dion|Ginette Reno|Isabelle Boulay|1",
            "¿Qué postre quebequés es un bizcocho bañado en sirope?|Pouding chômeur|Tarta Tatin|Far bretón|Clafoutis|0",
            "¿En qué región está la Roca Percé?|Abitibi|Charlevoix|Gaspesia|Saguenay|2",
            "¿En qué región está el monte Tremblant?|Los Laurentides|Gaspesia|Charlevoix|Estrie|0",
            "¿Qué día se celebra la Fiesta Nacional de Quebec?|El 1 de julio|El 24 de junio|El 1 de junio|El 21 de junio|1",
            "¿Qué río pasa por Quebec y Montreal?|El San Lorenzo|El Ottawa|El Saguenay|El Richelieu|0",
            "¿Qué deporte practica el CF Montréal?|Béisbol|Fútbol|Baloncesto|Hockey|1",
            "¿En qué isla está construida la ciudad de Montreal?|Isla de Orleans|Isla de Montreal|Isla Bizard|Isla Sainte-Hélène|1",
            "¿Qué provincia limita con Quebec por el oeste?|Manitoba|Ontario|Nuevo Brunswick|Saskatchewan|1"
        }
    };

    /** word : forbidden , forbidden , forbidden , forbidden */
    private static final String[][] TABOO = {
        {
            "Plage:sable,mer,soleil,vacances", "Pizza:fromage,Italie,four,tomate", "Vélo:roues,pédaler,guidon,selle",
            "Chocolat:cacao,sucre,noir,tablette", "Hôpital:médecin,malade,infirmière,soigner", "Neige:froid,hiver,flocon,ski",
            "Chien:aboyer,animal,niche,laisse", "Cinéma:film,écran,popcorn,salle", "Anniversaire:gâteau,bougies,cadeau,fête",
            "Avion:voler,pilote,aéroport,ailes", "Guitare:cordes,musique,jouer,électrique", "Pirate:bateau,trésor,mer,crochet",
            "Lune:nuit,ciel,étoile,astronaute", "Parapluie:pluie,ouvrir,mouillé,abri", "Football:ballon,but,joueur,équipe",
            "Livre:lire,pages,auteur,bibliothèque", "Téléphone:appeler,portable,sonner,écran", "Pompier:feu,camion,eau,casque",
            "Château:roi,prince,médiéval,forteresse", "Café:tasse,boire,noir,caféine", "Mariage:robe,époux,église,bague",
            "Pluie:eau,nuage,mouillé,parapluie", "Jardin:fleurs,plantes,herbe,arroser", "Fromage:lait,vache,manger,pâte"
        },
        {
            "Beach:sand,sea,sun,holiday", "Pizza:cheese,Italy,oven,tomato", "Bicycle:wheels,pedal,handlebars,saddle",
            "Chocolate:cocoa,sugar,dark,bar", "Hospital:doctor,sick,nurse,treat", "Snow:cold,winter,flake,ski",
            "Dog:bark,animal,kennel,leash", "Cinema:film,screen,popcorn,theatre", "Birthday:cake,candles,present,party",
            "Plane:fly,pilot,airport,wings", "Guitar:strings,music,play,electric", "Pirate:ship,treasure,sea,hook",
            "Moon:night,sky,star,astronaut", "Umbrella:rain,open,wet,shelter", "Football:ball,goal,player,team",
            "Book:read,pages,author,library", "Telephone:call,mobile,ring,screen", "Firefighter:fire,truck,water,helmet",
            "Castle:king,prince,medieval,fortress", "Coffee:cup,drink,black,caffeine", "Wedding:dress,groom,church,ring",
            "Rain:water,cloud,wet,umbrella", "Garden:flowers,plants,grass,water", "Cheese:milk,cow,eat,dairy"
        },
        {
            "Playa:arena,mar,sol,vacaciones", "Pizza:queso,Italia,horno,tomate", "Bicicleta:ruedas,pedalear,manillar,sillín",
            "Chocolate:cacao,azúcar,negro,tableta", "Hospital:médico,enfermo,enfermera,curar", "Nieve:frío,invierno,copo,esquí",
            "Perro:ladrar,animal,caseta,correa", "Cine:película,pantalla,palomitas,sala", "Cumpleaños:tarta,velas,regalo,fiesta",
            "Avión:volar,piloto,aeropuerto,alas", "Guitarra:cuerdas,música,tocar,eléctrica", "Pirata:barco,tesoro,mar,garfio",
            "Luna:noche,cielo,estrella,astronauta", "Paraguas:lluvia,abrir,mojado,refugio", "Fútbol:balón,gol,jugador,equipo",
            "Libro:leer,páginas,autor,biblioteca", "Teléfono:llamar,móvil,sonar,pantalla", "Bombero:fuego,camión,agua,casco",
            "Castillo:rey,príncipe,medieval,fortaleza", "Café:taza,beber,negro,cafeína", "Boda:vestido,novio,iglesia,anillo",
            "Lluvia:agua,nube,mojado,paraguas", "Jardín:flores,plantas,césped,regar", "Queso:leche,vaca,comer,lácteo"
        }
    };

    /** Same order in each language: things that can be drawn. */
    private static final String[][] DRAW = {
        {"chat", "maison", "soleil", "arbre", "voiture", "vélo", "bateau", "avion", "pizza", "banane", "fleur", "lune", "étoile", "livre",
         "téléphone", "lunettes", "chapeau", "escalier", "pont", "montagne", "plage", "parapluie", "guitare", "piano", "requin", "girafe",
         "éléphant", "papillon", "araignée", "clé", "horloge", "fusée", "robot", "dragon", "château", "pirate", "fantôme", "gâteau",
         "glace", "hamburger", "couronne", "balançoire", "tente", "lampe", "bonhomme de neige", "sapin", "cadeau", "ballon", "escargot", "pomme"},
        {"cat", "house", "sun", "tree", "car", "bicycle", "boat", "plane", "pizza", "banana", "flower", "moon", "star", "book",
         "telephone", "glasses", "hat", "stairs", "bridge", "mountain", "beach", "umbrella", "guitar", "piano", "shark", "giraffe",
         "elephant", "butterfly", "spider", "key", "clock", "rocket", "robot", "dragon", "castle", "pirate", "ghost", "cake",
         "ice cream", "hamburger", "crown", "swing", "tent", "lamp", "snowman", "fir tree", "present", "balloon", "snail", "apple"},
        {"gato", "casa", "sol", "árbol", "coche", "bicicleta", "barco", "avión", "pizza", "plátano", "flor", "luna", "estrella", "libro",
         "teléfono", "gafas", "sombrero", "escalera", "puente", "montaña", "playa", "paraguas", "guitarra", "piano", "tiburón", "jirafa",
         "elefante", "mariposa", "araña", "llave", "reloj", "cohete", "robot", "dragón", "castillo", "pirata", "fantasma", "tarta",
         "helado", "hamburguesa", "corona", "columpio", "tienda de campaña", "lámpara", "muñeco de nieve", "abeto", "regalo", "globo", "caracol", "manzana"}
    };

    private static int L() { return Math.max(0, Math.min(2, T.lang())); }

    /** The questions of a category (0 general, 1 France, 2 Québec, 3 everything) in the current language. */
    static String[] quizPool(int cat) {
        int l = L();
        java.util.List<String> o = new java.util.ArrayList<String>();
        if (cat == 0 || cat == 3) java.util.Collections.addAll(o, QUIZ[l]);
        if (cat == 1 || cat == 3) java.util.Collections.addAll(o, FRA[l]);
        if (cat == 2 || cat == 3) java.util.Collections.addAll(o, QUE[l]);
        return o.toArray(new String[0]);
    }

    static int tabooCount() { return TABOO[0].length; }
    /** [0] word, [1..4] forbidden */
    static String[] taboo(int i) {
        String e = TABOO[L()][i % TABOO[0].length];
        int c = e.indexOf(':');
        String[] f = e.substring(c + 1).split(",");
        String[] o = new String[1 + f.length];
        o[0] = e.substring(0, c);
        System.arraycopy(f, 0, o, 1, f.length);
        return o;
    }

    static int drawCount() { return DRAW[0].length; }
    static String drawWord(int i) { return DRAW[L()][i % DRAW[0].length]; }
}
