package io.farkle.dignifiedfarkleservice.controller;

import io.farkle.dignifiedfarkleservice.model.PointTally;
import io.farkle.dignifiedfarkleservice.model.dao.GameRepository;
import io.farkle.dignifiedfarkleservice.model.entity.Action;
import io.farkle.dignifiedfarkleservice.model.entity.Game;
import io.farkle.dignifiedfarkleservice.model.entity.Game.State;
import io.farkle.dignifiedfarkleservice.model.entity.GamePlayer;
import io.farkle.dignifiedfarkleservice.model.entity.Player;
import io.farkle.dignifiedfarkleservice.model.pojo.GamePreference;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("games")
public class GameController {

  private final GameRepository repository;

  public GameController(GameRepository repository) {
    this.repository = repository;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Game> get() {
    return repository.getAllBy();
  }

  @GetMapping(value = "{id:\\d+}/action", produces = MediaType.APPLICATION_JSON_VALUE)
  public Action getLastAction(@PathVariable long id, Authentication authentication) {
    List<Action> actions = get(id, authentication).getActions();
    return actions.get(actions.size() - 1);
  }

  @PostMapping(value = "join", produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Game join(@RequestBody GamePreference preference, Authentication authentication) {
    Random rnd = new Random();
    Player player = (Player) authentication.getPrincipal();
    Game game = repository
        .findFirstByStateAndPreferredNumPlayers(State.PENDING, preference.getNumPlayers())
        .orElseGet(() -> {
          Game g = new Game();
          g.setPreferredNumPlayers(preference.getNumPlayers());
          return repository.save(g);
        });
    for (GamePlayer gamePlayer : game.getGamePlayers()) {
      if (gamePlayer.getPlayer().getId() == player.getId()) {
        throw new IllegalArgumentException();
      }
    }

//    action.setGame(game);
//    action.setAvailableDice(new int[]{1, 2, 3});
//    action.setFrozenDice(new int[]{2, 2, 2});
//    action.setGame(game);

    GamePlayer gamePlayer = new GamePlayer();
    gamePlayer.setGame(game);
    gamePlayer.setPlayer(player);
    game.getGamePlayers().add(gamePlayer);
    if (game.getGamePlayers().size() == preference.getNumPlayers()) {
      int[] availableDiceArray = new int[6];
      Action action = new Action();
      action.setGame(game);
      for (int i = 0; i < availableDiceArray.length; i++) {
        availableDiceArray[i] = rnd.nextInt(6) + 1;
      }

      action.setAvailableDice(availableDiceArray);
      action.setNextPlayer(game.getGamePlayers().get(0).getPlayer());
      game.getActions().add(action);

      game.setState(State.IN_PROGRESS);
      game.setYourTurn(game.getLastAction().getNextPlayer().getId() == player.getId());
      System.out.println("GamePlayerID: " + gamePlayer.getId());
      System.out.println();

    }
    return repository.save(game);
  }

  @GetMapping(value = "{id:\\d+}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Game get(@PathVariable long id, Authentication auth) {
    Player player = (Player) auth.getPrincipal();
    Game game = repository.findById(id).get();
    List<GamePlayer> gamePlayers = game.getGamePlayers();
    for (GamePlayer gamePlayer : gamePlayers) {
      if (gamePlayer.getPlayer().getId() == player.getId()) {
        game.setYourTurn(game.getLastAction().getNextPlayer().getId() == player.getId());
        return game;
      }
    }
    throw new NoSuchElementException();
  }

  @PostMapping(value = "{id:\\d+}/actions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Game post(@PathVariable long id, @RequestBody Action action, Authentication auth) {
    Player player = (Player) auth.getPrincipal();
    Random rnd = new Random();
    int diceSendBack = 0;
    int sumDice = 0;

    Game game = get(id, auth);
    if (!game.isYourTurn()) {
      throw new IllegalArgumentException();
    }

    int[] frozenDice = action.getFrozenDice();
    System.out.println("Length Frozen:" + frozenDice.length);
//    refactoredFrozenDice = new int[sumDice];
    ArrayList<Integer> refactoredFrozenDice = new ArrayList<>();
    for (int i = 0; i < action.getFrozenDice().length; i++) {
      if (frozenDice[i] != 0) {
        refactoredFrozenDice.add(frozenDice[i]);
        sumDice = sumDice + 1;
      }
    }
    List<GamePlayer> gamePlayers = game.getGamePlayers();
    for (GamePlayer gamePlayer : gamePlayers) {
      if (gamePlayer.getPlayer().getId() == player.getId()) {
        gamePlayer.setPoints(gamePlayer.getPoints() + PointTally.DiceTally(refactoredFrozenDice));
        System.out.println("DONE: " + PointTally.DiceTally(refactoredFrozenDice));
      }
    }


    System.out.println("Number of Frozen Dice" + sumDice);
    System.out.println("Refactord " + refactoredFrozenDice);

    System.out.println("Stay? " + game.getLastAction().getStay());
    if (!action.getStay()){
      for (int i = 0; i < refactoredFrozenDice.size(); i++) {
        diceSendBack = frozenDice.length - refactoredFrozenDice.size();
      }
    } else {
      action.setPlayer(game.getLastAction().getNextPlayer());
      game.getLastAction().getNextPlayer();
      diceSendBack = 6;
    }

    if(frozenDice.length - refactoredFrozenDice.size() == 0) {
      diceSendBack = 6;
    }

    System.out.println("SendBack " + diceSendBack);

    int[] sendBackRandomDice = new int[diceSendBack];

    for (int i = 0; i < sendBackRandomDice.length; i++) {
      sendBackRandomDice[i] = rnd.nextInt(6) + 1;
    }

    ArrayList<Integer> refactoredSendBackRandomDice = new ArrayList<>();

    for (int i = 0; i < sendBackRandomDice.length; i++) {
      refactoredSendBackRandomDice.add(sendBackRandomDice[i]);
    }


    int sendBackTest = PointTally.DiceTally(refactoredSendBackRandomDice);
    if(sendBackTest == 3) {
      action.setFarkleOut(true);
      for (GamePlayer gamePlayer : gamePlayers) {
        if (gamePlayer.getPlayer().getId() == player.getId()) {
          gamePlayer.setPoints(gamePlayer.getPoints() - 3 - gamePlayer.getPoints());
        }
      }
    }
    if(sendBackRandomDice.length != 0) {
      action.setAvailableDice(sendBackRandomDice);
    } else {
      for (int i = 0; i < 6; i++) {
        sendBackRandomDice[i] = rnd.nextInt(6) + 1;
      }
      action.setAvailableDice(sendBackRandomDice);
    }

    action.setNextPlayer(player); // FIXME Should be a ternary, based on whether current player farkled out, ended play, or is continuing

    // TODO Validate and Process
    int turn = game.getLastAction().getTurn() + 1;
    action.setTurn(turn);
    action.setGame(game);
    action.setPlayer(player);

//    action.setFrozenDice(game.getLastAction().getFrozenDice());
    game.getActions().add(action);
    return repository.save(game);

  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NoSuchElementException.class)
  public void notFound() {
  }

//  @ResponseStatus(HttpStatus.BAD_REQUEST)
//  @ExceptionHandler(IllegalArgumentException.class)
//  public void notAllowed() {
//  }

}